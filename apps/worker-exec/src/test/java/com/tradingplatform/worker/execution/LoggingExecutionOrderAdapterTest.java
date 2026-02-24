package com.tradingplatform.worker.execution;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoggingExecutionOrderAdapterTest {
  @Test
  void shouldAcceptSubmitOrderCommand() {
    LoggingExecutionOrderAdapter adapter = new LoggingExecutionOrderAdapter();
    SubmitOrderCommand command =
        new SubmitOrderCommand(
            "ord-1",
            "acc-1",
            "BTCUSDT",
            "BUY",
            "LIMIT",
            new BigDecimal("0.01"),
            new BigDecimal("40000.00"),
            Instant.parse("2026-02-24T12:00:00Z"),
            "ord-1",
            UUID.randomUUID());

    assertDoesNotThrow(() -> adapter.submitOrder(command));
  }
}
