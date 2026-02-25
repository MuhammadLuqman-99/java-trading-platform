package com.tradingplatform.worker.connector;

import java.time.Instant;
import java.util.UUID;

public record ConnectorReplayRequest(
    UUID id,
    String connectorName,
    ConnectorReplayTriggerType triggerType,
    String reason,
    ConnectorReplayRequestStatus status,
    String requestedBy,
    Instant requestedAt,
    Instant startedAt,
    Instant completedAt,
    String errorCode,
    String errorMessage) {}
