package com.tradingplatform.tradingapi.portfolio;

import java.math.BigDecimal;

public record PositionView(String symbol, BigDecimal netQty) {}
