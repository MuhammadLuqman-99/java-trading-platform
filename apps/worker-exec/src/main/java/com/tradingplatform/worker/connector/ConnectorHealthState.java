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
    Instant updatedAt) {
  public static ConnectorHealthState initial(String connectorName, Instant now) {
    return new ConnectorHealthState(
        connectorName, ConnectorHealthStatus.DOWN, null, null, null, null, null, null, 0, 0, now);
  }
}
