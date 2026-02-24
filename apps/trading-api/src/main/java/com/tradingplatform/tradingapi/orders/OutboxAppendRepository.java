package com.tradingplatform.tradingapi.orders;

import com.tradingplatform.domain.orders.Order;
import com.tradingplatform.domain.orders.OrderStatus;
import com.tradingplatform.infra.kafka.contract.payload.BalanceUpdatedV1;
import java.time.Instant;
import java.util.UUID;

public interface OutboxAppendRepository {
  void appendOrderSubmitted(Order order, String correlationId, Instant occurredAt);

  void appendOrderUpdated(
      Order order, OrderStatus fromStatus, String correlationId, Instant occurredAt);

  void appendBalanceUpdated(UUID accountId, BalanceUpdatedV1 payload);
}
