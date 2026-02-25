package com.tradingplatform.integration.binance;

public interface BinanceOrderGateway {
  BinanceCancelOrderResult cancelOrder(BinanceCancelOrderRequest request);

  BinanceQueryOrderResult queryOrder(BinanceQueryOrderRequest request);
}
