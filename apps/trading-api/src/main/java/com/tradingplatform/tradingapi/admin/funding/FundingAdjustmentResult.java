package com.tradingplatform.tradingapi.admin.funding;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FundingAdjustmentResult(
    UUID accountId,
    String asset,
    FundingDirection direction,
    BigDecimal amount,
    BigDecimal available,
    BigDecimal reserved,
    String reason,
    Instant occurredAt) {}
