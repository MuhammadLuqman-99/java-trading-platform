package com.tradingplatform.worker.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventRecord(
    UUID id,
    String aggregateType,
    String aggregateId,
    String eventType,
    String eventPayload,
    String topic,
    String eventKey,
    String status,
    int attemptCount,
    Instant createdAt) {}
