package com.tradingplatform.tradingapi.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountLimitConfig(
    UUID accountId, BigDecimal maxOrderNotional, int priceBandBps, String updatedBy, Instant updatedAt) {}
