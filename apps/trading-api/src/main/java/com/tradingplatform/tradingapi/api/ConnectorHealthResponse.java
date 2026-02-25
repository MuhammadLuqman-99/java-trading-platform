package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.connector.ConnectorHealthSnapshot;
import java.time.Instant;

public record ConnectorHealthResponse(
    String connector,
    String status,
    Instant lastSuccessAt,
    Instant lastPollStartedAt,
    Instant lastPollCompletedAt,
    Instant lastErrorAt,
    String lastErrorCode,
    String lastErrorMessage,
    int openOrdersFetched,
    int recentTradesFetched,
    boolean stale) {
  public static ConnectorHealthResponse from(ConnectorHealthSnapshot snapshot, boolean stale) {
    return new ConnectorHealthResponse(
        snapshot.connectorName(),
        snapshot.status().name(),
        snapshot.lastSuccessAt(),
        snapshot.lastPollStartedAt(),
        snapshot.lastPollCompletedAt(),
        snapshot.lastErrorAt(),
        snapshot.lastErrorCode(),
        snapshot.lastErrorMessage(),
        snapshot.openOrdersFetched(),
        snapshot.recentTradesFetched(),
        stale);
  }
}
