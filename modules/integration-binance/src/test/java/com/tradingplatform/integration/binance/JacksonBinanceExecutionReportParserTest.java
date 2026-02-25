package com.tradingplatform.integration.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JacksonBinanceExecutionReportParserTest {
  private final JacksonBinanceExecutionReportParser parser =
      new JacksonBinanceExecutionReportParser(new ObjectMapper());

  @Test
  void shouldParseTradeExecutionReport() {
    String rawPayload = fixture("execution-report-trade.json");

    Optional<BinanceExecutionReport> result = parser.parse(rawPayload);

    assertTrue(result.isPresent());
    BinanceExecutionReport report = result.get();
    assertEquals("PARTIALLY_FILLED", report.externalOrderStatus());
    assertEquals("9001001", report.exchangeOrderId());
    assertEquals("ord-1001", report.exchangeClientOrderId());
    assertEquals("BTCUSDT", report.symbol());
    assertEquals("BUY", report.side());
    assertEquals("7001001", report.exchangeTradeId());
    assertEquals(new BigDecimal("0.25000000"), report.lastExecutedQty());
    assertEquals(new BigDecimal("0.75000000"), report.cumulativeExecutedQty());
    assertEquals(new BigDecimal("43000.10"), report.lastExecutedPrice());
    assertEquals("USDT", report.feeAsset());
    assertEquals(new BigDecimal("0.0005"), report.feeAmount());
    assertEquals(Instant.parse("2026-02-26T00:00:00Z"), report.tradeTime());
    assertEquals(rawPayload, report.rawPayload());
  }

  @Test
  void shouldIgnoreNonTradeExecutionType() {
    String rawPayload = fixture("execution-report-new.json");

    Optional<BinanceExecutionReport> result = parser.parse(rawPayload);

    assertTrue(result.isEmpty());
  }

  @Test
  void shouldIgnoreTradeEventWithoutPositiveTradeId() {
    String rawPayload = fixture("execution-report-trade-missing-trade-id.json");

    Optional<BinanceExecutionReport> result = parser.parse(rawPayload);

    assertTrue(result.isEmpty());
  }

  private static String fixture(String fileName) {
    String path = "fixtures/binance/" + fileName;
    try (InputStream inputStream =
        JacksonBinanceExecutionReportParserTest.class.getClassLoader().getResourceAsStream(path)) {
      if (inputStream == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to read fixture: " + path, ex);
    }
  }
}
