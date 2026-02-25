package com.tradingplatform.integration.binance;

public record BinanceCancelOrderRequest(String symbol, Long exchangeOrderId, String clientOrderId) {}
