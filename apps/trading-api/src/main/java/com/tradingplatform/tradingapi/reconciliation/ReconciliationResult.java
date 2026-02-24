package com.tradingplatform.tradingapi.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ReconciliationResult(
    Instant startedAt,
    Instant finishedAt,
    ReconciliationStatus status,
    Map<String, BigDecimal> driftByAsset,
    String notes) {}
