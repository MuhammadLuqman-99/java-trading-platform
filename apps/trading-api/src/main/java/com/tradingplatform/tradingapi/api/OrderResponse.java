package com.tradingplatform.tradingapi.api;

import com.tradingplatform.domain.orders.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    UUID accountId,
    String instrument,
    String side,
    String type,
    BigDecimal qty,
    BigDecimal price,
    String status,
    BigDecimal filledQty,
    String clientOrderId,
    String exchangeName,
    String exchangeOrderId,
    String exchangeClientOrderId,
    Instant createdAt,
    Instant updatedAt) {

  public static OrderResponse from(Order order) {
    return new OrderResponse(
        order.id(),
        order.accountId(),
        order.instrument(),
        order.side().name(),
        order.type().name(),
        order.qty(),
        order.price(),
        order.status().name(),
        order.filledQty(),
        order.clientOrderId(),
        order.exchangeName(),
        order.exchangeOrderId(),
        order.exchangeClientOrderId(),
        order.createdAt(),
        order.updatedAt());
  }
}
