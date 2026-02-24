package com.tradingplatform.tradingapi.idempotency.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyPersistenceApi {
  Optional<IdempotencyRecord> findByScopeAndKey(String scope, String idempotencyKey);

  IdempotencyRecord createInProgress(
      String scope, String idempotencyKey, String requestHash, Instant expiresAt);

  void markCompleted(UUID id, int responseCode, String responseBody);

  void markFailed(UUID id, String errorCode);
}
