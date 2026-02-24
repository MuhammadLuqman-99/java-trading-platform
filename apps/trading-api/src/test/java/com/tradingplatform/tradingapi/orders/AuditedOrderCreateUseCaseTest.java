package com.tradingplatform.tradingapi.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.domain.orders.Order;
import com.tradingplatform.domain.orders.OrderSide;
import com.tradingplatform.domain.orders.OrderType;
import com.tradingplatform.tradingapi.audit.AuditLogEntry;
import com.tradingplatform.tradingapi.audit.AuditLogRepository;
import com.tradingplatform.tradingapi.audit.AuditResult;
import com.tradingplatform.tradingapi.risk.RiskViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class AuditedOrderCreateUseCaseTest {
  private OrderCreateUseCase delegate;
  private AuditLogRepository auditLogRepository;
  private AuditedOrderCreateUseCase useCase;

  @BeforeEach
  void setUp() {
    delegate = org.mockito.Mockito.mock(OrderCreateUseCase.class);
    auditLogRepository = org.mockito.Mockito.mock(AuditLogRepository.class);
    useCase =
        new AuditedOrderCreateUseCase(
            delegate, auditLogRepository, new ObjectMapper().registerModule(new JavaTimeModule()));
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldWriteSuccessAudit() {
    CreateOrderCommand command = command();
    Order created =
        Order.createNew(
            command.orderId(),
            command.accountId(),
            command.instrument(),
            command.side(),
            command.type(),
            command.qty(),
            command.price(),
            command.clientOrderId(),
            command.occurredAt());
    when(delegate.create(command)).thenReturn(created);
    setJwtSubject("user-123");

    Order result = useCase.create(command);

    assertEquals(created.id(), result.id());
    ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
    verify(auditLogRepository).append(captor.capture());
    AuditLogEntry entry = captor.getValue();
    assertEquals("user-123", entry.actorUserId());
    assertEquals(AuditResult.SUCCESS, entry.result());
    assertEquals("ORDER_SUBMIT", entry.action());
  }

  @Test
  void shouldWriteRejectedAuditOnRiskViolation() {
    CreateOrderCommand command = command();
    when(delegate.create(command))
        .thenThrow(new RiskViolationException("MAX_NOTIONAL_EXCEEDED", "notional too large"));

    RiskViolationException ex =
        assertThrows(RiskViolationException.class, () -> useCase.create(command));
    assertEquals("MAX_NOTIONAL_EXCEEDED", ex.code());

    ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
    verify(auditLogRepository).append(captor.capture());
    assertEquals(AuditResult.REJECTED, captor.getValue().result());
    assertEquals("MAX_NOTIONAL_EXCEEDED", captor.getValue().errorCode());
  }

  @Test
  void shouldWriteFailedAuditOnUnexpectedException() {
    CreateOrderCommand command = command();
    when(delegate.create(command)).thenThrow(new IllegalStateException("boom"));

    assertThrows(IllegalStateException.class, () -> useCase.create(command));
    ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
    verify(auditLogRepository).append(captor.capture());
    assertEquals(AuditResult.FAILED, captor.getValue().result());
  }

  private static void setJwtSubject(String subject) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject)
            .claim("sub", subject)
            .build();
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null));
  }

  private static CreateOrderCommand command() {
    return new CreateOrderCommand(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "BTCUSDT",
        OrderSide.BUY,
        OrderType.LIMIT,
        new BigDecimal("1"),
        new BigDecimal("50000"),
        "client-1",
        "corr-1",
        Instant.now());
  }
}
