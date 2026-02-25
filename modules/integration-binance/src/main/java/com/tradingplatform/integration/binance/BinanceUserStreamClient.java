package com.tradingplatform.integration.binance;

public interface BinanceUserStreamClient {
  void start(BinanceUserStreamEventHandler eventHandler);

  void stop();

  boolean isConnected();

  long reconnectAttempts();
}
