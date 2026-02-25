package com.tradingplatform.integration.binance;

public record BinanceQueryOrderRequest(String symbol, Long exchangeOrderId, String clientOrderId) {}
