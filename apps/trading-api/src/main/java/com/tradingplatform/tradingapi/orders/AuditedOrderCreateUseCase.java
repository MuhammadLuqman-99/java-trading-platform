package com.tradingplatform.tradingapi.orders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.orders.Order;
import com.tradingplatform.tradingapi.audit.AuditLogEntry;
import com.tradingplatform.tradingapi.audit.AuditLogRepository;
import com.tradingplatform.tradingapi.audit.AuditResult;
import com.tradingplatform.tradingapi.risk.RiskViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@Primary
public class AuditedOrderCreateUseCase implements OrderCreateUseCase {
  private final OrderCreateUseCase delegate;
  private final AuditLogRepository auditLogRepository;
  private final ObjectMapper objectMapper;

  public AuditedOrderCreateUseCase(
      @Qualifier("riskValidatedOrderCreateUseCase") OrderCreateUseCase delegate,
      AuditLogRepository auditLogRepository,
      ObjectMapper objectMapper) {
    this.delegate = delegate;
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public Order create(CreateOrderCommand command) {
    String actorUserId = resolveActorUserId();
    String entityId = command.orderId() != null ? command.orderId().toString() : "unknown";
    String metadataJson = toJson(createMetadata(command, Instant.now()));
    try {
      Order created = delegate.create(command);
      auditLogRepository.append(
          new AuditLogEntry(
              actorUserId,
              "ORDER_SUBMIT",
              "ORDER",
              created.id().toString(),
              null,
              toJson(orderSnapshot(created)),
              AuditResult.SUCCESS,
              null,
              null,
              metadataJson));
      return created;
    } catch (RiskViolationException ex) {
      auditLogRepository.append(
          new AuditLogEntry(
              actorUserId,
              "ORDER_SUBMIT",
              "ORDER",
              entityId,
              null,
              null,
              AuditResult.REJECTED,
              ex.code(),
              ex.getMessage(),
              metadataJson));
      throw ex;
    } catch (RuntimeException ex) {
      auditLogRepository.append(
          new AuditLogEntry(
              actorUserId,
              "ORDER_SUBMIT",
              "ORDER",
              entityId,
              null,
              null,
              AuditResult.FAILED,
              "UNEXPECTED_ERROR",
              ex.getMessage(),
              metadataJson));
      throw ex;
    }
  }

  private static String resolveActorUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return null;
    }
    Object principal = authentication.getPrincipal();
    if (principal instanceof Jwt jwt) {
      return jwt.getSubject();
    }
    if (principal instanceof String text && !text.isBlank() && !"anonymousUser".equals(text)) {
      return text;
    }
    return null;
  }

  private static Map<String, Object> createMetadata(CreateOrderCommand command, Instant now) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("accountId", command.accountId());
    payload.put("instrument", command.instrument());
    payload.put("side", command.side());
    payload.put("type", command.type());
    payload.put("qty", command.qty());
    payload.put("price", command.price());
    payload.put("clientOrderId", command.clientOrderId());
    payload.put("correlationId", command.correlationId());
    payload.put("occurredAt", command.occurredAt() == null ? now : command.occurredAt());
    return payload;
  }

  private static Map<String, Object> orderSnapshot(Order order) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", order.id());
    payload.put("accountId", order.accountId());
    payload.put("instrument", order.instrument());
    payload.put("side", order.side());
    payload.put("type", order.type());
    payload.put("qty", order.qty());
    payload.put("price", order.price());
    payload.put("status", order.status());
    payload.put("filledQty", order.filledQty());
    payload.put("clientOrderId", order.clientOrderId());
    payload.put("exchangeOrderId", order.exchangeOrderId());
    payload.put("createdAt", order.createdAt());
    payload.put("updatedAt", order.updatedAt());
    return payload;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize audit payload", ex);
    }
  }
}
