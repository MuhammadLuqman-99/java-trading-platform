package com.tradingplatform.tradingapi.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PortfolioResponse(
    UUID accountId,
    List<BalanceItemResponse> balances,
    List<PositionResponse> positions,
    Instant asOf) {}
