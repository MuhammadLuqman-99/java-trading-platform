package com.tradingplatform.infra.kafka.contract.payload;

import java.math.BigDecimal;
import java.time.Instant;

public record ExecutionRecordedV1(
    String executionId,
    String orderId,
    String accountId,
    String tradeId,
    BigDecimal qty,
    BigDecimal price,
    String feeAsset,
    BigDecimal feeAmount,
    Instant executedAt) {}
