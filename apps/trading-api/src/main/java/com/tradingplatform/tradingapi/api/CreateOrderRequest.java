package com.tradingplatform.tradingapi.api;

import com.tradingplatform.domain.orders.OrderSide;
import com.tradingplatform.domain.orders.OrderType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
    @NotNull UUID accountId,
    @NotBlank String symbol,
    @NotNull OrderSide side,
    @NotNull OrderType type,
    @NotNull @DecimalMin(value = "0.000000000000000001", inclusive = true) BigDecimal qty,
    BigDecimal price,
    String clientOrderId) {

  @AssertTrue(message = "price must be > 0 for LIMIT and null for MARKET")
  public boolean isPriceValidForOrderType() {
    if (type == null) {
      return true;
    }
    if (type == OrderType.MARKET) {
      return price == null;
    }
    return price != null && price.compareTo(BigDecimal.ZERO) > 0;
  }
}
