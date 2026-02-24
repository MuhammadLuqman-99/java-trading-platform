package com.tradingplatform.tradingapi.api;

import java.util.List;
import java.util.UUID;

public record BalancesResponse(UUID accountId, List<BalanceItemResponse> balances) {}
