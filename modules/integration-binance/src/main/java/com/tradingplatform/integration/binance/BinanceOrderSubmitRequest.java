package com.tradingplatform.integration.binance;

import java.math.BigDecimal;

public record BinanceOrderSubmitRequest(
    String symbol,
    String side,
    String type,
    BigDecimal quantity,
    BigDecimal price,
    String newClientOrderId) {
  public BinanceOrderSubmitRequest {
    requireNonBlank(symbol, "symbol");
    requireNonBlank(side, "side");
    requireNonBlank(type, "type");
    if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("quantity must be > 0");
    }
    requireNonBlank(newClientOrderId, "newClientOrderId");

    if ("LIMIT".equalsIgnoreCase(type)) {
      if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("LIMIT order requires price > 0");
      }
    } else if (price != null) {
      throw new IllegalArgumentException("Only LIMIT order can include price");
    }
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
  }
}
