package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.instruments.InstrumentConfigView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InstrumentResponse(
    UUID id,
    String symbol,
    String status,
    BigDecimal referencePrice,
    Instant createdAt,
    Instant updatedAt) {
  public static InstrumentResponse from(InstrumentConfigView view) {
    return new InstrumentResponse(
        view.id(),
        view.symbol(),
        view.status(),
        view.referencePrice(),
        view.createdAt(),
        view.updatedAt());
  }
}
