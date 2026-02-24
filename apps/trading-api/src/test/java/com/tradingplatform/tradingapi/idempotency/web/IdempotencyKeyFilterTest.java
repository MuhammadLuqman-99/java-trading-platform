package com.tradingplatform.tradingapi.idempotency.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.tradingapi.idempotency.hash.RequestHashCalculator;
import com.tradingplatform.tradingapi.idempotency.persistence.IdempotencyPersistenceApi;
import com.tradingplatform.tradingapi.idempotency.persistence.IdempotencyRecord;
import com.tradingplatform.tradingapi.idempotency.persistence.IdempotencyStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class IdempotencyKeyFilterTest {
  private static final String SCOPE = "POST:/v1/orders";
  private static final String IDEMPOTENCY_KEY = "key-123";
  private static final String HASH = "hash-abc";

  private IdempotencyPersistenceApi persistenceApi;
  private RequestHashCalculator requestHashCalculator;
  private IdempotencyKeyFilter filter;

  @BeforeEach
  void setUp() {
    persistenceApi = org.mockito.Mockito.mock(IdempotencyPersistenceApi.class);
    requestHashCalculator = org.mockito.Mockito.mock(RequestHashCalculator.class);
    filter =
        new IdempotencyKeyFilter(
            defaultProperties(),
            new IdempotencyPathMatcher(),
            persistenceApi,
            requestHashCalculator,
            new ObjectMapper());
  }

  @Test
  void shouldBypassWhenRequestIsOutsideOptInPaths() throws Exception {
    MockHttpServletRequest request = request("POST", "/v1/version", "{}");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (req, res) ->
            ((MockHttpServletResponse) res).setStatus(MockHttpServletResponse.SC_NO_CONTENT));

    assertEquals(MockHttpServletResponse.SC_NO_CONTENT, response.getStatus());
    verifyNoInteractions(persistenceApi);
    verifyNoInteractions(requestHashCalculator);
  }

  @Test
  void shouldReturnBadRequestWhenHeaderIsMissing() throws Exception {
    MockHttpServletRequest request = request("POST", "/v1/orders", "{\"symbol\":\"BTCUSDT\"}");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, noOpChain());

    assertEquals(MockHttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertEquals("missing", response.getHeader("X-Idempotency-Status"));
    verifyNoInteractions(persistenceApi);
  }

  @Test
  void shouldReturnConflictWhenExistingRequestHashDiffers() throws Exception {
    MockHttpServletRequest request = request("POST", "/v1/orders", "{\"symbol\":\"BTCUSDT\"}");
    request.addHeader("Idempotency-Key", IDEMPOTENCY_KEY);
    MockHttpServletResponse response = new MockHttpServletResponse();

    when(requestHashCalculator.compute(any(), any())).thenReturn(HASH);
    when(persistenceApi.findByScopeAndKey(SCOPE, IDEMPOTENCY_KEY))
        .thenReturn(Optional.of(record(IdempotencyStatus.COMPLETED, "other-hash")));

    filter.doFilter(request, response, noOpChain());

    assertEquals(MockHttpServletResponse.SC_CONFLICT, response.getStatus());
    assertEquals("mismatch", response.getHeader("X-Idempotency-Status"));
    verify(persistenceApi, never()).createInProgress(any(), any(), any(), any());
  }

  @Test
  void shouldCreateAndCompleteRecordForNewRequest() throws Exception {
    MockHttpServletRequest request = request("POST", "/v1/orders", "{\"symbol\":\"BTCUSDT\"}");
    request.addHeader("Idempotency-Key", IDEMPOTENCY_KEY);
    MockHttpServletResponse response = new MockHttpServletResponse();
    IdempotencyRecord created = record(IdempotencyStatus.IN_PROGRESS, HASH);

    when(requestHashCalculator.compute(any(), any())).thenReturn(HASH);
    when(persistenceApi.findByScopeAndKey(SCOPE, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
    when(persistenceApi.createInProgress(eq(SCOPE), eq(IDEMPOTENCY_KEY), eq(HASH), any()))
        .thenReturn(created);

    filter.doFilter(
        request,
        response,
        (req, res) -> {
          MockHttpServletResponse httpResponse = (MockHttpServletResponse) res;
          httpResponse.setStatus(MockHttpServletResponse.SC_CREATED);
          httpResponse.setContentType("application/json");
          httpResponse.getWriter().write("{\"orderId\":\"ord-1\"}");
        });

    assertEquals(MockHttpServletResponse.SC_CREATED, response.getStatus());
    assertEquals("new", response.getHeader("X-Idempotency-Status"));
    verify(persistenceApi)
        .markCompleted(created.id(), MockHttpServletResponse.SC_CREATED, "{\"orderId\":\"ord-1\"}");
  }

  @Test
  void shouldReplayCompletedRequestWithSameHash() throws Exception {
    MockHttpServletRequest request = request("POST", "/v1/orders", "{\"symbol\":\"BTCUSDT\"}");
    request.addHeader("Idempotency-Key", IDEMPOTENCY_KEY);
    MockHttpServletResponse response = new MockHttpServletResponse();

    when(requestHashCalculator.compute(any(), any())).thenReturn(HASH);
    when(persistenceApi.findByScopeAndKey(SCOPE, IDEMPOTENCY_KEY))
        .thenReturn(
            Optional.of(
                record(
                    IdempotencyStatus.COMPLETED,
                    HASH,
                    MockHttpServletResponse.SC_ACCEPTED,
                    "{\"orderId\":\"abc\"}")));

    filter.doFilter(request, response, noOpChain());

    assertEquals(MockHttpServletResponse.SC_ACCEPTED, response.getStatus());
    assertEquals("replayed", response.getHeader("X-Idempotency-Status"));
    assertEquals("{\"orderId\":\"abc\"}", response.getContentAsString());
    verify(persistenceApi, never()).createInProgress(any(), any(), any(), any());
    verify(persistenceApi, never()).markCompleted(any(), any(int.class), any());
  }

  @Test
  void shouldMarkFailedWhenDownstreamThrows() {
    MockHttpServletRequest request = request("POST", "/v1/orders", "{\"symbol\":\"BTCUSDT\"}");
    request.addHeader("Idempotency-Key", IDEMPOTENCY_KEY);
    MockHttpServletResponse response = new MockHttpServletResponse();
    IdempotencyRecord created = record(IdempotencyStatus.IN_PROGRESS, HASH);

    when(requestHashCalculator.compute(any(), any())).thenReturn(HASH);
    when(persistenceApi.findByScopeAndKey(SCOPE, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
    when(persistenceApi.createInProgress(eq(SCOPE), eq(IDEMPOTENCY_KEY), eq(HASH), any()))
        .thenReturn(created);

    assertThrows(
        ServletException.class,
        () ->
            filter.doFilter(
                request,
                response,
                (req, res) -> {
                  throw new IllegalStateException("boom");
                }));

    verify(persistenceApi).markFailed(created.id(), "UNHANDLED_EXCEPTION");
  }

  @Test
  void shouldHandleInsertRaceByReReadingExistingRecord() throws Exception {
    MockHttpServletRequest request = request("POST", "/v1/orders", "{\"symbol\":\"BTCUSDT\"}");
    request.addHeader("Idempotency-Key", IDEMPOTENCY_KEY);
    MockHttpServletResponse response = new MockHttpServletResponse();
    IdempotencyRecord existing = record(IdempotencyStatus.IN_PROGRESS, HASH);

    when(requestHashCalculator.compute(any(), any())).thenReturn(HASH);
    when(persistenceApi.findByScopeAndKey(SCOPE, IDEMPOTENCY_KEY))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(existing));
    when(persistenceApi.createInProgress(eq(SCOPE), eq(IDEMPOTENCY_KEY), eq(HASH), any()))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    filter.doFilter(request, response, noOpChain());

    assertEquals(MockHttpServletResponse.SC_CONFLICT, response.getStatus());
    assertEquals("in_progress", response.getHeader("X-Idempotency-Status"));
    verify(persistenceApi, never()).markCompleted(any(), any(int.class), any());
  }

  private static MockHttpServletRequest request(String method, String uri, String body) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setContentType("application/json");
    request.setCharacterEncoding(StandardCharsets.UTF_8.name());
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    return request;
  }

  private static FilterChain noOpChain() {
    return (req, res) -> {
      // Intentionally empty for tests that validate early exits.
    };
  }

  private static IdempotencyProperties defaultProperties() {
    IdempotencyProperties properties = new IdempotencyProperties();
    properties.setEnabled(true);
    properties.setRequiredHeader("Idempotency-Key");
    properties.setTtlHours(24);
    properties.setOptInPaths(java.util.List.of("/v1/orders/**"));
    return properties;
  }

  private static IdempotencyRecord record(IdempotencyStatus status, String requestHash) {
    return record(status, requestHash, null, null);
  }

  private static IdempotencyRecord record(
      IdempotencyStatus status, String requestHash, Integer responseCode, String responseBody) {
    Instant now = Instant.now();
    return new IdempotencyRecord(
        UUID.randomUUID(),
        IDEMPOTENCY_KEY,
        SCOPE,
        requestHash,
        status,
        responseCode,
        responseBody,
        null,
        now,
        now,
        now.plusSeconds(60));
  }
}
