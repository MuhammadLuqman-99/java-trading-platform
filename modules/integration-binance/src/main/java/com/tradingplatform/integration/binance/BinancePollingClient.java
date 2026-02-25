package com.tradingplatform.integration.binance;

import java.time.Instant;
import java.util.List;

public interface BinancePollingClient {
  List<BinanceOpenOrderSnapshot> fetchOpenOrders();

  List<BinanceTradeSnapshot> fetchRecentTrades(String symbol, Instant fromInclusive);
}
