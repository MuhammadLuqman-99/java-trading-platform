package com.tradingplatform.integration.binance;

import java.util.List;

public interface ExchangeOrderStatusMappingRepository {
  List<ExchangeOrderStatusMapping> findAll();
}
