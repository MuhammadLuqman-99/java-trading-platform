package com.tradingplatform.tradingapi.connector;

import java.time.Instant;

public interface ConnectorReplayRequestRepository {
  ConnectorReplaySubmission createManualReplayRequest(
      String connectorName, String reason, String requestedBy, Instant requestedAt);
}
