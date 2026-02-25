package com.tradingplatform.integration.binance;

public interface BinanceRestClient {
  long getServerTime();

  BinanceAccountInfo getAccountInfo();
}
