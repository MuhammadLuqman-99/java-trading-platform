package com.tradingplatform.integration.binance;

public record BinanceOpenOrderSnapshot(
    String symbol, String clientOrderId, String exchangeOrderId, String status) {}
