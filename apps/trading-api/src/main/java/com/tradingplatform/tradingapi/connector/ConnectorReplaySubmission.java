package com.tradingplatform.tradingapi.connector;

import java.time.Instant;
import java.util.UUID;

public record ConnectorReplaySubmission(
    UUID requestId, String connectorName, String status, Instant requestedAt, String requestedBy) {}
