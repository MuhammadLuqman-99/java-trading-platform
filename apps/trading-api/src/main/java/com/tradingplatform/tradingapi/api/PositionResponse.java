package com.tradingplatform.tradingapi.api;

import java.math.BigDecimal;

public record PositionResponse(String symbol, BigDecimal netQty) {}
