package com.tradingplatform.infra.kafka.contract.payload;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceUpdatedV1(
        String accountId,
        String asset,
        BigDecimal available,
        BigDecimal reserved,
        String reason,
        Instant asOf
) {
}
