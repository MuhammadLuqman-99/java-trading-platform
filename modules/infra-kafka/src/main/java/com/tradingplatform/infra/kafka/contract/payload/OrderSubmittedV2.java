package com.tradingplatform.infra.kafka.contract.payload;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSubmittedV2(
    String orderId,
    String accountId,
    String instrument,
    String side,
    String type,
    BigDecimal qty,
    BigDecimal price,
    String clientOrderId,
    Instant submittedAt) {}
