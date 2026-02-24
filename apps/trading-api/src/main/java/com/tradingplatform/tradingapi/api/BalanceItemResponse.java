package com.tradingplatform.tradingapi.api;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceItemResponse(
    String asset, BigDecimal available, BigDecimal reserved, BigDecimal total, Instant updatedAt) {}
