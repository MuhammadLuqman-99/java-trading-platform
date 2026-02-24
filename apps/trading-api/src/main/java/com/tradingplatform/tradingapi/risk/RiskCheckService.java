package com.tradingplatform.tradingapi.risk;

import com.tradingplatform.tradingapi.orders.CreateOrderCommand;

public interface RiskCheckService {
  void validateOrder(CreateOrderCommand command);
}
