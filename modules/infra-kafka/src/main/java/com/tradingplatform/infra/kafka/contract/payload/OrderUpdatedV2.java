package com.tradingplatform.infra.kafka.contract.payload;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderUpdatedV2(
    String orderId,
    String accountId,
    String status,
    BigDecimal filledQty,
    BigDecimal remainingQty,
    String exchangeName,
    String exchangeOrderId,
    String exchangeClientOrderId,
    Instant updatedAt) {}
