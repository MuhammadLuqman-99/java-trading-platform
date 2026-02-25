package com.tradingplatform.worker.execution.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tradingplatform.integration.binance.BinanceExecutionReport;
import com.tradingplatform.integration.binance.BinanceExecutionReportParser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExecutionReportIngestionServiceTest {
  @Test
  void shouldReturnIgnoredWhenParserReturnsEmpty() {
    BinanceExecutionReportParser parser = org.mockito.Mockito.mock(BinanceExecutionReportParser.class);
    ExecutionReportProcessor processor = org.mockito.Mockito.mock(ExecutionReportProcessor.class);
    when(parser.parse("{}")).thenReturn(Optional.empty());
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ExecutionReportIngestionService service =
        new ExecutionReportIngestionService(parser, processor, meterRegistry);

    ExecutionIngestionResult result = service.ingest("{}");

    assertEquals(ExecutionIngestionResult.IGNORED, result);
    assertEquals(
        1.0,
        meterRegistry.counter("worker.execution.ingest.total", "outcome", "ignored").count());
    verifyNoInteractions(processor);
  }

  @Test
  void shouldCountDeduplicatedIngestion() {
    BinanceExecutionReportParser parser = org.mockito.Mockito.mock(BinanceExecutionReportParser.class);
    ExecutionReportProcessor processor = org.mockito.Mockito.mock(ExecutionReportProcessor.class);
    BinanceExecutionReport report = sampleReport();
    when(parser.parse(report.rawPayload())).thenReturn(Optional.of(report));
    when(processor.process(report)).thenReturn(ExecutionIngestionResult.DUPLICATE);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ExecutionReportIngestionService service =
        new ExecutionReportIngestionService(parser, processor, meterRegistry);

    ExecutionIngestionResult result = service.ingest(report.rawPayload());

    assertEquals(ExecutionIngestionResult.DUPLICATE, result);
    assertEquals(
        1.0,
        meterRegistry.counter("worker.execution.ingest.total", "outcome", "duplicate").count());
    assertEquals(1.0, meterRegistry.counter("worker.execution.dedupe.hit.total").count());
    verify(processor).process(report);
  }

  @Test
  void shouldCountErrorOutcomeWhenParserThrows() {
    BinanceExecutionReportParser parser = org.mockito.Mockito.mock(BinanceExecutionReportParser.class);
    ExecutionReportProcessor processor = org.mockito.Mockito.mock(ExecutionReportProcessor.class);
    when(parser.parse("bad-json")).thenThrow(new IllegalArgumentException("bad json"));
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ExecutionReportIngestionService service =
        new ExecutionReportIngestionService(parser, processor, meterRegistry);

    assertThrows(IllegalArgumentException.class, () -> service.ingest("bad-json"));
    assertEquals(
        1.0,
        meterRegistry.counter("worker.execution.ingest.total", "outcome", "error").count());
    verifyNoInteractions(processor);
  }

  private static BinanceExecutionReport sampleReport() {
    return new BinanceExecutionReport(
        "PARTIALLY_FILLED",
        "9001001",
        "ord-1001",
        "BTCUSDT",
        "BUY",
        "7001001",
        new BigDecimal("0.25"),
        new BigDecimal("0.75"),
        new BigDecimal("43000.10"),
        "USDT",
        new BigDecimal("0.0005"),
        Instant.parse("2026-02-26T00:00:00Z"),
        "{\"e\":\"executionReport\"}");
  }
}
