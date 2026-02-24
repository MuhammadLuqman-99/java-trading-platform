package com.tradingplatform.tradingapi.orders;

public interface OrderEventRepository {
  void append(OrderEventAppend event);
}
