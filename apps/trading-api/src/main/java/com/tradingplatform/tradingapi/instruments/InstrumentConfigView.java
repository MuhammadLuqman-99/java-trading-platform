package com.tradingplatform.tradingapi.instruments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InstrumentConfigView(
    UUID id,
    String symbol,
    String status,
    BigDecimal referencePrice,
    Instant createdAt,
    Instant updatedAt) {}
