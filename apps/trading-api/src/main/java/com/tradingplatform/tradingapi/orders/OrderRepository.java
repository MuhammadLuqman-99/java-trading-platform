package com.tradingplatform.tradingapi.orders;

import com.tradingplatform.domain.orders.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
  void insert(Order order);

  Optional<Order> findById(UUID orderId);

  void update(Order order);

  List<Order> findByAccountId(
      UUID accountId, String status, String instrument, int offset, int limit);

  long countByAccountId(UUID accountId, String status, String instrument);
}
