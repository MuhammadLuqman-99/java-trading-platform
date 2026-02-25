package com.tradingplatform.worker.connector;

import java.time.Instant;

public record ConnectorHealthState(
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
    ConnectorWsConnectionState wsConnectionState,
    Instant lastWsConnectedAt,
    Instant lastWsDisconnectedAt,
    Instant lastWsErrorAt,
    String lastWsErrorCode,
    String lastWsErrorMessage,
    long wsReconnectAttempts,
    Instant updatedAt) {
  public static ConnectorHealthState initial(String connectorName, Instant now) {
    return new ConnectorHealthState(
        connectorName,
        ConnectorHealthStatus.DOWN,
        null,
        null,
        null,
        null,
        null,
        null,
        0,
        0,
        ConnectorWsConnectionState.DOWN,
        null,
        null,
        null,
        null,
        null,
        0L,
        now);
  }
}
