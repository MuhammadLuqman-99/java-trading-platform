package com.tradingplatform.tradingapi.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record FundingAdjustmentRequest(
    @NotNull UUID accountId,
    @NotBlank String asset,
    @NotNull @DecimalMin(value = "0.000000000000000001", inclusive = true) BigDecimal amount,
    @NotBlank String reason) {}
