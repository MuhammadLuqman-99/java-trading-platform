package com.tradingplatform.infra.kafka.contract.payload;

import java.math.BigDecimal;
import java.time.Instant;

public record ExecutionRecordedV2(
    String executionId,
    String orderId,
    String accountId,
    String exchangeName,
    String exchangeOrderId,
    String exchangeClientOrderId,
    String exchangeTradeId,
    String rawExecutionType,
    String rawOrderStatus,
    BigDecimal qty,
    BigDecimal price,
    String feeAsset,
    BigDecimal feeAmount,
    Instant executedAt) {}
