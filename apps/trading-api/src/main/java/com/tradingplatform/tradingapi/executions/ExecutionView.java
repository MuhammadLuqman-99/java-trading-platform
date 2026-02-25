package com.tradingplatform.tradingapi.executions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExecutionView(
    UUID id,
    UUID orderId,
    UUID accountId,
    String symbol,
    String side,
    String tradeId,
    String exchangeName,
    String exchangeOrderId,
    BigDecimal qty,
    BigDecimal price,
    String feeAsset,
    BigDecimal feeAmount,
    Instant executedAt) {}
