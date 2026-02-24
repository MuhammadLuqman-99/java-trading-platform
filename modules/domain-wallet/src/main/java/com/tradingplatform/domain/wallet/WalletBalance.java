package com.tradingplatform.domain.wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WalletBalance(
    UUID accountId,
    String asset,
    BigDecimal available,
    BigDecimal reserved,
    Instant updatedAt) {

  public WalletBalance {
    Objects.requireNonNull(accountId, "accountId must not be null");
    Objects.requireNonNull(asset, "asset must not be null");
    Objects.requireNonNull(available, "available must not be null");
    Objects.requireNonNull(reserved, "reserved must not be null");
    if (available.compareTo(BigDecimal.ZERO) < 0) {
      throw new WalletDomainException("available must be >= 0");
    }
    if (reserved.compareTo(BigDecimal.ZERO) < 0) {
      throw new WalletDomainException("reserved must be >= 0");
    }
  }
}
