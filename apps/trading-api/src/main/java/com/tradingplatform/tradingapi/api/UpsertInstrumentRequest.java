package com.tradingplatform.tradingapi.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpsertInstrumentRequest(
    @NotBlank(message = "status is required") String status,
    @NotNull(message = "referencePrice is required")
        @DecimalMin(
            value = "0",
            inclusive = false,
            message = "referencePrice must be greater than 0")
        BigDecimal referencePrice) {}
