package com.tradingplatform.domain.wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WalletReservation(
    UUID id,
    UUID accountId,
    String asset,
    BigDecimal amount,
    UUID orderId,
    ReservationStatus status,
    Instant createdAt,
    Instant releasedAt) {

  public WalletReservation {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(accountId, "accountId must not be null");
    Objects.requireNonNull(asset, "asset must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(orderId, "orderId must not be null");
    Objects.requireNonNull(status, "status must not be null");
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new WalletDomainException("amount must be > 0");
    }
  }
}
