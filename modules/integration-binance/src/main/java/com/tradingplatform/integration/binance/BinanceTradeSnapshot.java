package com.tradingplatform.integration.binance;

import java.time.Instant;

public record BinanceTradeSnapshot(
    String symbol, String tradeId, String exchangeOrderId, String side, Instant tradeTime) {}
