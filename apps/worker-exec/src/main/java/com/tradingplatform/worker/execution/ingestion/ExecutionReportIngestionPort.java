package com.tradingplatform.worker.execution.ingestion;

public interface ExecutionReportIngestionPort {
  ExecutionIngestionResult ingest(String rawExecutionReportPayload);
}
