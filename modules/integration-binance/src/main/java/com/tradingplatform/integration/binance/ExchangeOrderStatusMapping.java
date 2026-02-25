package com.tradingplatform.integration.binance;

import com.tradingplatform.domain.orders.OrderStatus;
import java.util.Objects;

public record ExchangeOrderStatusMapping(
    String venue,
    String externalStatus,
    OrderStatus domainStatus,
    boolean terminal,
    boolean cancelable) {
  public ExchangeOrderStatusMapping {
    Objects.requireNonNull(venue, "venue must not be null");
    Objects.requireNonNull(externalStatus, "externalStatus must not be null");
    Objects.requireNonNull(domainStatus, "domainStatus must not be null");
  }
}
