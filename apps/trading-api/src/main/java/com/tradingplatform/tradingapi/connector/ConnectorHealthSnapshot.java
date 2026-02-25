package com.tradingplatform.tradingapi.connector;

import java.time.Instant;

public record ConnectorHealthSnapshot(
    String connectorName,
    ConnectorHealthStatus status,
    Instant lastSuccessAt,
    Instant lastPollStartedAt,
    Instant lastPollCompletedAt,
    Instant lastErrorAt,
    String lastErrorCode,
    String lastErrorMessage,
    int openOrdersFetched,
    int recentTradesFetched,
    Instant updatedAt) {}
