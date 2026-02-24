package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.ledger.FundingDirection;
import java.math.BigDecimal;
import java.util.UUID;

public record FundingAdjustmentResponse(
    UUID transactionId,
    UUID accountId,
    String asset,
    FundingDirection direction,
    BigDecimal amount,
    String reason) {}
