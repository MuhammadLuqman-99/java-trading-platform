package com.tradingplatform.tradingapi.orders;

import com.tradingplatform.domain.orders.Order;

public interface OrderCreateUseCase {
  Order create(CreateOrderCommand command);
}
