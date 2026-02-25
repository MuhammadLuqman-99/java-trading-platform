package com.tradingplatform.worker.connector;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorReplayRequestRepository {
  Optional<ConnectorReplayRequest> claimNextPending(String connectorName, Instant claimedAt);

  void markSucceeded(UUID requestId, Instant completedAt);

  void markFailed(UUID requestId, String errorCode, String errorMessage, Instant completedAt);

  boolean enqueueRecoveryIfWindowClear(
      String connectorName,
      String reason,
      String requestedBy,
      Instant requestedAt,
      Duration dedupeWindow);

  int countPending(String connectorName);
}
