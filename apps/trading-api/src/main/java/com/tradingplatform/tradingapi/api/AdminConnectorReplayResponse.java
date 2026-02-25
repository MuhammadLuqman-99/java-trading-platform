package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.connector.ConnectorReplaySubmission;
import java.time.Instant;
import java.util.UUID;

public record AdminConnectorReplayResponse(
    UUID requestId, String connector, String status, Instant requestedAt, String requestedBy) {
  public static AdminConnectorReplayResponse from(ConnectorReplaySubmission submission) {
    return new AdminConnectorReplayResponse(
        submission.requestId(),
        submission.connectorName(),
        submission.status(),
        submission.requestedAt(),
        submission.requestedBy());
  }
}
