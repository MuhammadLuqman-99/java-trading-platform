package com.tradingplatform.worker.execution.ingestion;

public interface ExecutionRepository {
  boolean insertIfAbsent(ExecutionInsert execution);
}
