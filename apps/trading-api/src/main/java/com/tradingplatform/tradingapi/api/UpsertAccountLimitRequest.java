package com.tradingplatform.tradingapi.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpsertAccountLimitRequest(
    @NotNull @DecimalMin(value = "0.000000000000000001", inclusive = true) BigDecimal maxOrderNotional,
    Integer priceBandBps) {}
