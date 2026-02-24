package com.tradingplatform.tradingapi.orders;

import com.tradingplatform.domain.orders.Order;
import com.tradingplatform.domain.orders.OrderStatus;
import java.time.Instant;

public interface OutboxAppendRepository {
  void appendOrderSubmitted(Order order, String correlationId, Instant occurredAt);

  void appendOrderUpdated(
      Order order, OrderStatus fromStatus, String correlationId, Instant occurredAt);
}
