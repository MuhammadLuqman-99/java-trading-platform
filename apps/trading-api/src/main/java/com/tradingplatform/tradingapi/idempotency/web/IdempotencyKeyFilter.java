package com.tradingplatform.tradingapi.idempotency.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.tradingapi.idempotency.hash.RequestHashCalculator;
import com.tradingplatform.tradingapi.idempotency.persistence.IdempotencyPersistenceApi;
import com.tradingplatform.tradingapi.idempotency.persistence.IdempotencyRecord;
import com.tradingplatform.tradingapi.idempotency.persistence.IdempotencyStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(
    prefix = "idempotency",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class IdempotencyKeyFilter extends OncePerRequestFilter {
  private static final String TYPE_PREFIX = "/problems/";

  private final IdempotencyProperties properties;
  private final IdempotencyPathMatcher pathMatcher;
  private final IdempotencyPersistenceApi persistenceApi;
  private final RequestHashCalculator requestHashCalculator;
  private final ObjectMapper objectMapper;

  public IdempotencyKeyFilter(
      IdempotencyProperties properties,
      IdempotencyPathMatcher pathMatcher,
      IdempotencyPersistenceApi persistenceApi,
      RequestHashCalculator requestHashCalculator,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.pathMatcher = pathMatcher;
    this.persistenceApi = persistenceApi;
    this.requestHashCalculator = requestHashCalculator;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!properties.isEnabled() || !pathMatcher.shouldEnforce(request, properties)) {
      filterChain.doFilter(request, response);
      return;
    }

    String idempotencyKey = request.getHeader(properties.getRequiredHeader());
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      writeError(
          response,
          HttpStatus.BAD_REQUEST,
          "idempotency-key-required",
          "IDEMPOTENCY_KEY_REQUIRED",
          "Missing required header: " + properties.getRequiredHeader(),
          "missing");
      return;
    }

    CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
    String scope = buildScope(wrappedRequest);
    String requestHash =
        requestHashCalculator.compute(wrappedRequest, wrappedRequest.getCachedBody());

    Optional<IdempotencyRecord> existing = persistenceApi.findByScopeAndKey(scope, idempotencyKey);
    if (existing.isPresent()) {
      handleExisting(existing.get(), requestHash, response);
      return;
    }

    IdempotencyRecord createdRecord;
    try {
      createdRecord = createInProgress(scope, idempotencyKey, requestHash);
    } catch (DataIntegrityViolationException ex) {
      Optional<IdempotencyRecord> racedRecord =
          persistenceApi.findByScopeAndKey(scope, idempotencyKey);
      if (racedRecord.isPresent()) {
        handleExisting(racedRecord.get(), requestHash, response);
        return;
      }
      throw ex;
    }

    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
    responseWrapper.setHeader("X-Idempotency-Status", "new");
    try {
      filterChain.doFilter(wrappedRequest, responseWrapper);
      finalizeRecord(createdRecord, responseWrapper);
      responseWrapper.copyBodyToResponse();
    } catch (Exception ex) {
      persistenceApi.markFailed(createdRecord.id(), "UNHANDLED_EXCEPTION");
      if (ex instanceof ServletException servletException) {
        throw servletException;
      }
      if (ex instanceof IOException ioException) {
        throw ioException;
      }
      throw new ServletException(ex);
    }
  }

  private IdempotencyRecord createInProgress(
      String scope, String idempotencyKey, String requestHash) {
    long ttlHours = Math.max(1L, properties.getTtlHours());
    Instant expiresAt = Instant.now().plus(Duration.ofHours(ttlHours));
    return persistenceApi.createInProgress(scope, idempotencyKey, requestHash, expiresAt);
  }

  private void finalizeRecord(
      IdempotencyRecord createdRecord, ContentCachingResponseWrapper response) {
    int responseCode = response.getStatus();
    if (responseCode >= 500) {
      persistenceApi.markFailed(createdRecord.id(), "HTTP_" + responseCode);
      return;
    }
    persistenceApi.markCompleted(createdRecord.id(), responseCode, extractResponseBody(response));
  }

  private void handleExisting(
      IdempotencyRecord existing, String requestHash, HttpServletResponse response)
      throws IOException {
    if (existing.isExpired(Instant.now())) {
      writeError(
          response,
          HttpStatus.CONFLICT,
          "idempotency-key-expired",
          "IDEMPOTENCY_KEY_EXPIRED",
          "The idempotency key has expired and cannot be replayed.",
          "expired");
      return;
    }

    if (!existing.requestHash().equals(requestHash)) {
      writeError(
          response,
          HttpStatus.CONFLICT,
          "idempotency-request-mismatch",
          "IDEMPOTENCY_REQUEST_MISMATCH",
          "Idempotency key was already used with a different request payload.",
          "mismatch");
      return;
    }

    if (existing.status() == IdempotencyStatus.IN_PROGRESS) {
      writeError(
          response,
          HttpStatus.CONFLICT,
          "idempotency-in-progress",
          "IDEMPOTENCY_IN_PROGRESS",
          "A request with this idempotency key is still in progress.",
          "in_progress");
      return;
    }

    if (existing.status() == IdempotencyStatus.COMPLETED) {
      replayCompleted(existing, response);
      return;
    }

    writeError(
        response,
        HttpStatus.CONFLICT,
        "idempotency-previously-failed",
        "IDEMPOTENCY_PREVIOUSLY_FAILED",
        "A previous request for this idempotency key failed.",
        "previously_failed");
  }

  private void writeError(
      HttpServletResponse response,
      HttpStatus status,
      String problemType,
      String code,
      String message,
      String statusHeaderValue)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
    problem.setType(URI.create(TYPE_PREFIX + problemType));
    problem.setTitle("Idempotency Error");
    problem.setProperty("code", code);

    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader("X-Idempotency-Status", statusHeaderValue);
    objectMapper.writeValue(response.getWriter(), problem);
  }

  private void replayCompleted(IdempotencyRecord existing, HttpServletResponse response)
      throws IOException {
    if (existing.responseCode() == null) {
      writeError(
          response,
          HttpStatus.CONFLICT,
          "idempotency-duplicate-completed",
          "IDEMPOTENCY_DUPLICATE_COMPLETED",
          "A completed request already exists for this idempotency key.",
          "duplicate_completed");
      return;
    }
    response.setStatus(existing.responseCode());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader("X-Idempotency-Status", "replayed");
    if (existing.responseBody() != null && !existing.responseBody().isBlank()) {
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write(existing.responseBody());
    }
  }

  private String extractResponseBody(ContentCachingResponseWrapper response) {
    byte[] body = response.getContentAsByteArray();
    if (body == null || body.length == 0) {
      return null;
    }
    String encoding = response.getCharacterEncoding();
    if (encoding == null || encoding.isBlank()) {
      encoding = StandardCharsets.UTF_8.name();
    }
    return new String(body, java.nio.charset.Charset.forName(encoding));
  }

  private String buildScope(HttpServletRequest request) {
    return request.getMethod() + ":" + request.getRequestURI();
  }
}
