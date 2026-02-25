package com.tradingplatform.tradingapi.risk;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import com.tradingplatform.domain.orders.OrderSide;
import com.tradingplatform.domain.orders.OrderType;
import com.tradingplatform.tradingapi.orders.CreateOrderCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcRiskCheckServiceTest {
  private JdbcTemplate jdbcTemplate;
  private JdbcRiskCheckService service;

  @BeforeEach
  void setUp() {
    jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    service = new JdbcRiskCheckService(jdbcTemplate);
  }

  @Test
  void shouldAcceptLimitOrderWithinConstraints() {
    stubInstrument(activeInstrument());
    stubLimits(new AccountLimitView(UUID.randomUUID(), new BigDecimal("100000"), 500));

    assertDoesNotThrow(
        () ->
            service.validateOrder(
                command(OrderType.LIMIT, new BigDecimal("1"), new BigDecimal("51000"))));
  }

  @Test
  void shouldRejectInactiveInstrument() {
    stubInstrument(
        new InstrumentRiskView(
            UUID.randomUUID(),
            "BTCUSDT",
            "HALTED",
            new BigDecimal("50000"),
            new BigDecimal("0.10"),
            new BigDecimal("0.01"),
            new BigDecimal("0.01"),
            new BigDecimal("100"),
            new BigDecimal("10")));
    stubLimits(new AccountLimitView(UUID.randomUUID(), new BigDecimal("100000"), 500));

    RiskViolationException ex =
        assertThrows(
            RiskViolationException.class,
            () -> service.validateOrder(command(OrderType.MARKET, new BigDecimal("1"), null)));
    assertEquals("INSTRUMENT_NOT_TRADABLE", ex.code());
  }

  @Test
  void shouldRejectMaxNotionalExceeded() {
    stubInstrument(activeInstrument());
    stubLimits(new AccountLimitView(UUID.randomUUID(), new BigDecimal("10000"), 500));

    RiskViolationException ex =
        assertThrows(
            RiskViolationException.class,
            () ->
                service.validateOrder(
                    command(OrderType.LIMIT, new BigDecimal("1"), new BigDecimal("50001"))));
    assertEquals("MAX_NOTIONAL_EXCEEDED", ex.code());
  }

  @Test
  void shouldRejectPriceBandExceededForLimit() {
    stubInstrument(activeInstrument());
    stubLimits(new AccountLimitView(UUID.randomUUID(), new BigDecimal("1000000"), 100));

    RiskViolationException ex =
        assertThrows(
            RiskViolationException.class,
            () ->
                service.validateOrder(
                    command(OrderType.LIMIT, new BigDecimal("1"), new BigDecimal("56000"))));
    assertEquals("PRICE_BAND_EXCEEDED", ex.code());
  }

  @Test
  void shouldRejectQtyStepMismatch() {
    stubInstrument(activeInstrument());
    stubLimits(new AccountLimitView(UUID.randomUUID(), new BigDecimal("1000000"), 1000));

    RiskViolationException ex =
        assertThrows(
            RiskViolationException.class,
            () ->
                service.validateOrder(
                    command(OrderType.LIMIT, new BigDecimal("1.005"), new BigDecimal("50000.10"))));
    assertEquals("QTY_STEP_MISMATCH", ex.code());
  }

  @Test
  void shouldRejectPriceTickMismatch() {
    stubInstrument(activeInstrument());
    stubLimits(new AccountLimitView(UUID.randomUUID(), new BigDecimal("1000000"), 1000));

    RiskViolationException ex =
        assertThrows(
            RiskViolationException.class,
            () ->
                service.validateOrder(
                    command(OrderType.LIMIT, new BigDecimal("1"), new BigDecimal("50000.05"))));
    assertEquals("PRICE_TICK_MISMATCH", ex.code());
  }

  @Test
  void shouldRejectMinNotionalNotMet() {
    stubInstrument(activeInstrument());
    stubLimits(new AccountLimitView(UUID.randomUUID(), new BigDecimal("1000000"), 1000));

    RiskViolationException ex =
        assertThrows(
            RiskViolationException.class,
            () ->
                service.validateOrder(
                    command(OrderType.LIMIT, new BigDecimal("0.01"), new BigDecimal("500.00"))));
    assertEquals("MIN_NOTIONAL_NOT_MET", ex.code());
  }

  private void stubInstrument(InstrumentRiskView instrument) {
    when(
            jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("FROM instruments")),
                any(RowMapper.class),
                any()))
        .thenReturn(List.of(instrument));
  }

  private void stubLimits(AccountLimitView accountLimit) {
    when(
            jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("FROM account_limits")),
                any(RowMapper.class),
                any()))
        .thenReturn(List.of(accountLimit));
  }

  private static CreateOrderCommand command(OrderType type, BigDecimal qty, BigDecimal price) {
    return new CreateOrderCommand(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "BTCUSDT",
        OrderSide.BUY,
        type,
        qty,
        price,
        "client-1",
        "corr-1",
        Instant.now());
  }

  private static InstrumentRiskView activeInstrument() {
    return new InstrumentRiskView(
        UUID.randomUUID(),
        "BTCUSDT",
        "ACTIVE",
        new BigDecimal("50000"),
        new BigDecimal("0.10"),
        new BigDecimal("0.01"),
        new BigDecimal("0.01"),
        new BigDecimal("100"),
        new BigDecimal("10"));
  }
}
