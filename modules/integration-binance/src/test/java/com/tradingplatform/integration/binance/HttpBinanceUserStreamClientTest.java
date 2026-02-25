package com.tradingplatform.integration.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HttpBinanceUserStreamClientTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldParseExecutionReportPayload() {
    String payload =
        """
        {
          "e": "executionReport",
          "E": 1771977600000,
          "s": "BTCUSDT",
          "c": "6b8b4567-1234-4bba-a57c-f945f2999d01",
          "S": "BUY",
          "o": "LIMIT",
          "q": "0.01000000",
          "p": "42000.00000000",
          "x": "TRADE",
          "X": "PARTIALLY_FILLED",
          "i": 100200300,
          "l": "0.00500000",
          "z": "0.00500000",
          "L": "42100.00000000",
          "n": "0.21050000",
          "N": "USDT",
          "T": 1771977601000,
          "t": 501700
        }
        """;

    BinanceExecutionReportEvent event =
        HttpBinanceUserStreamClient.parseExecutionReport(payload, objectMapper);

    assertEquals("BTCUSDT", event.symbol());
    assertEquals("100200300", event.exchangeOrderId());
    assertEquals("6b8b4567-1234-4bba-a57c-f945f2999d01", event.exchangeClientOrderId());
    assertEquals("501700", event.exchangeTradeId());
    assertEquals("BUY", event.side());
    assertEquals("TRADE", event.executionType());
    assertEquals("PARTIALLY_FILLED", event.orderStatus());
    assertEquals(new BigDecimal("0.00500000"), event.lastExecutedQty());
    assertEquals(new BigDecimal("0.00500000"), event.cumulativeFilledQty());
    assertEquals(new BigDecimal("42100.00000000"), event.lastExecutedPrice());
    assertEquals(new BigDecimal("0.01000000"), event.orderQty());
    assertEquals(new BigDecimal("42000.00000000"), event.orderPrice());
    assertEquals(new BigDecimal("0.21050000"), event.feeAmount());
    assertEquals("USDT", event.feeAsset());
    assertEquals(Instant.ofEpochMilli(1771977600000L), event.eventTime());
    assertEquals(Instant.ofEpochMilli(1771977601000L), event.tradeTime());
  }

  @Test
  void shouldTreatNegativeTradeIdAsNull() {
    String payload =
        """
        {
          "e": "executionReport",
          "E": 1771977600000,
          "s": "ETHUSDT",
          "c": "ord-123",
          "S": "SELL",
          "q": "1",
          "p": "3000",
          "x": "NEW",
          "X": "NEW",
          "i": 5001,
          "l": "0",
          "z": "0",
          "L": "0",
          "n": "0",
          "N": null,
          "T": 0,
          "t": -1
        }
        """;

    BinanceExecutionReportEvent event =
        HttpBinanceUserStreamClient.parseExecutionReport(payload, objectMapper);

    assertNull(event.exchangeTradeId());
  }

  @Test
  void shouldRejectInvalidExecutionReportPayload() {
    assertThrows(
        IllegalArgumentException.class,
        () -> HttpBinanceUserStreamClient.parseExecutionReport("{not-valid-json}", objectMapper));
  }
}
