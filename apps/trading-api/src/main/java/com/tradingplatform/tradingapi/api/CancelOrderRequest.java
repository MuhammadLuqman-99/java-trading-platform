package com.tradingplatform.tradingapi.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CancelOrderRequest(@NotNull UUID accountId, String reason) {}
