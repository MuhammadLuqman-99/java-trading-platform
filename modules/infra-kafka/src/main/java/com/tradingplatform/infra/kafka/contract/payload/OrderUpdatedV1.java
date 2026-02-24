package com.tradingplatform.infra.kafka.contract.payload;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderUpdatedV1(
    String orderId,
    String accountId,
    String status,
    BigDecimal filledQty,
    BigDecimal remainingQty,
    String exchangeOrderId,
    Instant updatedAt) {}
