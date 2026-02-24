package com.tradingplatform.tradingapi.idempotency.persistence;

import java.time.Instant;
import java.util.UUID;

public record IdempotencyRecord(
    UUID id,
    String idempotencyKey,
    String scope,
    String requestHash,
    IdempotencyStatus status,
    Integer responseCode,
    String responseBody,
    String errorCode,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt) {
  public boolean isExpired(Instant now) {
    return expiresAt != null && now != null && expiresAt.isBefore(now);
  }
}
