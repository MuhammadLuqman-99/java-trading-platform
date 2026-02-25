package com.tradingplatform.integration.binance;

import java.math.BigDecimal;
import java.time.Instant;

public record BinanceTradeSnapshot(
    String symbol,
    String tradeId,
    String exchangeOrderId,
    String side,
    BigDecimal qty,
    BigDecimal price,
    String feeAsset,
    BigDecimal feeAmount,
    Instant tradeTime) {}
