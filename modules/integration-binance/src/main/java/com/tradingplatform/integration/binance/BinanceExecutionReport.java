package com.tradingplatform.integration.binance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record BinanceExecutionReport(
    String externalOrderStatus,
    String exchangeOrderId,
    String exchangeClientOrderId,
    String symbol,
    String side,
    String exchangeTradeId,
    BigDecimal lastExecutedQty,
    BigDecimal cumulativeExecutedQty,
    BigDecimal lastExecutedPrice,
    String feeAsset,
    BigDecimal feeAmount,
    Instant tradeTime,
    String rawPayload) {
  public BinanceExecutionReport {
    requireNonBlank(externalOrderStatus, "externalOrderStatus");
    requireNonBlank(exchangeOrderId, "exchangeOrderId");
    requireNonBlank(exchangeClientOrderId, "exchangeClientOrderId");
    requireNonBlank(symbol, "symbol");
    requireNonBlank(side, "side");
    requireNonBlank(exchangeTradeId, "exchangeTradeId");
    Objects.requireNonNull(lastExecutedQty, "lastExecutedQty must not be null");
    Objects.requireNonNull(cumulativeExecutedQty, "cumulativeExecutedQty must not be null");
    Objects.requireNonNull(lastExecutedPrice, "lastExecutedPrice must not be null");
    requireNonBlank(feeAsset, "feeAsset");
    Objects.requireNonNull(feeAmount, "feeAmount must not be null");
    Objects.requireNonNull(tradeTime, "tradeTime must not be null");
    requireNonBlank(rawPayload, "rawPayload");
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}
