package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.admin.funding.FundingDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record AdminFundingAdjustmentRequest(
    @NotNull UUID accountId,
    @NotBlank String asset,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
    @NotNull FundingDirection direction,
    @NotBlank String reason) {}
