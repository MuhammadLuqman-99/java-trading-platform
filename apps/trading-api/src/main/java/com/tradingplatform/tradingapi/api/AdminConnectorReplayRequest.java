package com.tradingplatform.tradingapi.api;

import jakarta.validation.constraints.Size;

public record AdminConnectorReplayRequest(
    @Size(max = 64) String connector, @Size(max = 200) String reason) {}
