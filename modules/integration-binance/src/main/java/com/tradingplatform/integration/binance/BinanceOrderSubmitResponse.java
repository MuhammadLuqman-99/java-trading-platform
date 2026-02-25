package com.tradingplatform.integration.binance;

public record BinanceOrderSubmitResponse(String exchangeOrderId, String clientOrderId, String status) {}
