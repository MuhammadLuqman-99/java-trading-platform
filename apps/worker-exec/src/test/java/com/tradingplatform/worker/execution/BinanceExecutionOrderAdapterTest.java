package com.tradingplatform.worker.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tradingplatform.integration.binance.BinanceOrderClient;
import com.tradingplatform.integration.binance.BinanceOrderSubmitRequest;
import com.tradingplatform.integration.binance.BinanceOrderSubmitResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BinanceExecutionOrderAdapterTest {
  @Test
  void shouldMapOrderIdToNewClientOrderId() {
    CapturingBinanceOrderClient client = new CapturingBinanceOrderClient();
    BinanceExecutionOrderAdapter adapter = new BinanceExecutionOrderAdapter(client);
    SubmitOrderCommand command =
        new SubmitOrderCommand(
            "6b8b4567-1234-4bba-a57c-f945f2999d01",
            "6b8b4567-1234-4bba-a57c-f945f2999d02",
            "BTCUSDT",
            "BUY",
            "LIMIT",
            new BigDecimal("0.01"),
            new BigDecimal("40000.10"),
            "client-1001",
            Instant.parse("2026-02-24T12:00:00Z"),
            "corr-1001",
            UUID.randomUUID());

    ExecutionAckResult result = adapter.placeOrder(command);

    assertNotNull(client.lastRequest);
    assertEquals("6b8b4567-1234-4bba-a57c-f945f2999d01", client.lastRequest.newClientOrderId());
    assertEquals("BTCUSDT", client.lastRequest.symbol());
    assertEquals("BUY", client.lastRequest.side());
    assertEquals("LIMIT", client.lastRequest.type());
    assertEquals(new BigDecimal("0.01"), client.lastRequest.quantity());
    assertEquals(new BigDecimal("40000.10"), client.lastRequest.price());
    assertEquals("BINANCE", result.exchangeName());
    assertEquals("binance-123", result.exchangeOrderId());
    assertEquals("6b8b4567-1234-4bba-a57c-f945f2999d01", result.exchangeClientOrderId());
  }

  private static final class CapturingBinanceOrderClient implements BinanceOrderClient {
    private BinanceOrderSubmitRequest lastRequest;

    @Override
    public BinanceOrderSubmitResponse submitOrder(BinanceOrderSubmitRequest request) {
      this.lastRequest = request;
      return new BinanceOrderSubmitResponse("binance-123", request.newClientOrderId(), "NEW");
    }
  }
}
