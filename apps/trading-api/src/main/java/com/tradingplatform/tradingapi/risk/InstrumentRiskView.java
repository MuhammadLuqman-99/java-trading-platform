package com.tradingplatform.tradingapi.risk;

import java.math.BigDecimal;
import java.util.UUID;

public record InstrumentRiskView(
    UUID id,
    String symbol,
    String status,
    BigDecimal referencePrice,
    BigDecimal tickSize,
    BigDecimal stepSize,
    BigDecimal minQty,
    BigDecimal maxQty,
    BigDecimal minNotional) {
  public boolean isActive() {
    return "ACTIVE".equals(status);
  }
}
