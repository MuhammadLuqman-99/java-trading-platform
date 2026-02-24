package com.tradingplatform.tradingapi.api;

import java.util.UUID;

public record CancelOrderResponse(UUID orderId, String status) {}
