package com.tradingplatform.worker.execution.ingestion;

import com.tradingplatform.integration.binance.BinanceExecutionReport;
import com.tradingplatform.integration.binance.BinanceExecutionReportParser;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ExecutionReportIngestionService implements ExecutionReportIngestionPort {
  private static final String INGEST_TOTAL_METRIC = "worker.execution.ingest.total";
  private static final String INGEST_DURATION_METRIC = "worker.execution.ingest.duration";
  private static final String DEDUPE_HIT_TOTAL_METRIC = "worker.execution.dedupe.hit.total";

  private final BinanceExecutionReportParser parser;
  private final ExecutionReportProcessor processor;
  private final MeterRegistry meterRegistry;

  public ExecutionReportIngestionService(
      BinanceExecutionReportParser parser,
      ExecutionReportProcessor processor,
      MeterRegistry meterRegistry) {
    this.parser = parser;
    this.processor = processor;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public ExecutionIngestionResult ingest(String rawExecutionReportPayload) {
    Timer.Sample timerSample = Timer.start(meterRegistry);
    try {
      Optional<BinanceExecutionReport> parsed = parser.parse(rawExecutionReportPayload);
      if (parsed.isEmpty()) {
        increment("ignored");
        return ExecutionIngestionResult.IGNORED;
      }
      ExecutionIngestionResult result = processor.process(parsed.get());
      increment(result == ExecutionIngestionResult.DUPLICATE ? "duplicate" : "processed");
      if (result == ExecutionIngestionResult.DUPLICATE) {
        meterRegistry.counter(DEDUPE_HIT_TOTAL_METRIC).increment();
      }
      return result;
    } catch (RuntimeException ex) {
      increment("error");
      throw ex;
    } finally {
      timerSample.stop(Timer.builder(INGEST_DURATION_METRIC).register(meterRegistry));
    }
  }

  private void increment(String outcome) {
    meterRegistry.counter(INGEST_TOTAL_METRIC, "outcome", outcome).increment();
  }
}
