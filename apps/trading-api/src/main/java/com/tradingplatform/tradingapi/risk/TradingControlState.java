package com.tradingplatform.tradingapi.risk;

import java.time.Instant;

public record TradingControlState(
    boolean tradingFrozen, String freezeReason, String updatedBy, Instant updatedAt) {}
