package com.tradingplatform.integration.binance;

import com.tradingplatform.domain.orders.OrderStatus;

public interface ExchangeOrderStatusMapper {
  OrderStatus toDomainStatus(String venue, String externalStatus);
}
