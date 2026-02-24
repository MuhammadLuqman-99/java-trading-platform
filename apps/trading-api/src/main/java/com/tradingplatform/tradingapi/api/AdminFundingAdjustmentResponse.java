package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.admin.funding.FundingAdjustmentResult;
import com.tradingplatform.tradingapi.admin.funding.FundingDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminFundingAdjustmentResponse(
    UUID accountId,
    String asset,
    FundingDirection direction,
    BigDecimal amount,
    BigDecimal available,
    BigDecimal reserved,
    String reason,
    Instant occurredAt) {
  public static AdminFundingAdjustmentResponse from(FundingAdjustmentResult result) {
    return new AdminFundingAdjustmentResponse(
        result.accountId(),
        result.asset(),
        result.direction(),
        result.amount(),
        result.available(),
        result.reserved(),
        result.reason(),
        result.occurredAt());
  }
}
