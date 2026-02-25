package com.tradingplatform.worker.execution;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
            "6b8b4567-1234-4bba-a57c-f945f2999d01",
            "6b8b4567-1234-4bba-a57c-f945f2999d02",
            "BTCUSDT",
            "BUY",
            "LIMIT",
            new BigDecimal("0.01"),
            new BigDecimal("40000.00"),
            "client-1001",
            Instant.parse("2026-02-24T12:00:00Z"),
            "corr-1001",
            UUID.randomUUID());

    ExecutionAckResult result = assertDoesNotThrow(() -> adapter.placeOrder(command));
    assertEquals("BINANCE", result.exchangeName());
    assertEquals("6b8b4567-1234-4bba-a57c-f945f2999d01", result.exchangeClientOrderId());
  }
}
