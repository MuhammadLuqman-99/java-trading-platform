package com.tradingplatform.integration.binance;

import java.math.BigDecimal;

public record BinanceAccountBalance(String asset, BigDecimal free, BigDecimal locked) {}
