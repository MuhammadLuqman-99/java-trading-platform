package com.tradingplatform.tradingapi.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletBalanceView(
    UUID accountId, String asset, BigDecimal available, BigDecimal reserved, Instant updatedAt) {}
