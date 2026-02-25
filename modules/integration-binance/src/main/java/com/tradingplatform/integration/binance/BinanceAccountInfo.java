package com.tradingplatform.integration.binance;

import java.util.List;

public record BinanceAccountInfo(boolean canTrade, String accountType, List<BinanceAccountBalance> balances) {}
