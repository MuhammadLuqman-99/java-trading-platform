package com.tradingplatform.worker.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {
  List<OutboxEventRecord> findPendingBatch(int limit);

  void markPublished(UUID id, Instant publishedAt);

  void markFailed(UUID id, String errorMessage);
}
