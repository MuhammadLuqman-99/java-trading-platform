package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.risk.AccountLimitConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountLimitResponse(
    UUID accountId, BigDecimal maxOrderNotional, int priceBandBps, String updatedBy, Instant updatedAt) {
  public static AccountLimitResponse from(AccountLimitConfig config) {
    return new AccountLimitResponse(
        config.accountId(),
        config.maxOrderNotional(),
        config.priceBandBps(),
        config.updatedBy(),
        config.updatedAt());
  }
}
