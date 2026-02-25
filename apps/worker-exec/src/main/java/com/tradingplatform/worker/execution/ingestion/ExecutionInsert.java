package com.tradingplatform.worker.execution.ingestion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExecutionInsert(
    UUID id,
    UUID orderId,
    UUID accountId,
    String instrument,
    String tradeId,
    String exchangeName,
    String exchangeOrderId,
    String side,
    BigDecimal qty,
    BigDecimal price,
    String feeAsset,
    BigDecimal feeAmount,
    Instant executedAt) {}
