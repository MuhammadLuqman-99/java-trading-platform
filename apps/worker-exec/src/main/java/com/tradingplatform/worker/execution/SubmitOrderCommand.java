package com.tradingplatform.worker.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubmitOrderCommand(
    String orderId,
    String accountId,
    String instrument,
    String side,
    String type,
    BigDecimal qty,
    BigDecimal price,
    Instant submittedAt,
    String correlationId,
    UUID eventId) {}
