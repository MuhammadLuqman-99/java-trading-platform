package com.tradingplatform.domain.wallet;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientBalanceException extends WalletDomainException {
  private final UUID accountId;
  private final String asset;
  private final BigDecimal requested;
  private final BigDecimal available;

  public InsufficientBalanceException(
      UUID accountId, String asset, BigDecimal requested, BigDecimal available) {
    super(
        String.format(
            "Insufficient %s balance for account %s: requested=%s, available=%s",
            asset, accountId, requested, available));
    this.accountId = accountId;
    this.asset = asset;
    this.requested = requested;
    this.available = available;
  }

  public UUID accountId() {
    return accountId;
  }

  public String asset() {
    return asset;
  }

  public BigDecimal requested() {
    return requested;
  }

  public BigDecimal available() {
    return available;
  }
}
