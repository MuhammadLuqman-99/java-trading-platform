package com.tradingplatform.integration.binance;

public interface BinanceOrderClient {
  BinanceOrderSubmitResponse submitOrder(BinanceOrderSubmitRequest request);
}
