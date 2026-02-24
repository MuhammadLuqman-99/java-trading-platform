package com.tradingplatform.tradingapi.orders;

import java.time.Instant;
import java.util.UUID;

public record CancelOrderCommand(
    UUID orderId, UUID accountId, String reason, String correlationId, Instant occurredAt) {}
