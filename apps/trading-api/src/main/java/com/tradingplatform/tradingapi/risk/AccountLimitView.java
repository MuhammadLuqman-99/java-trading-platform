package com.tradingplatform.tradingapi.risk;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountLimitView(UUID accountId, BigDecimal maxOrderNotional, int priceBandBps) {}
