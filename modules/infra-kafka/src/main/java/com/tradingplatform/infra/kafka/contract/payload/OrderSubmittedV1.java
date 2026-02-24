package com.tradingplatform.infra.kafka.contract.payload;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSubmittedV1(
        String orderId,
        String accountId,
        String instrument,
        String side,
        String type,
        BigDecimal qty,
        BigDecimal price,
        Instant submittedAt
) {
}
