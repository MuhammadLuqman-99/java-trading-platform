package com.tradingplatform.tradingapi.connector;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConnectorReplayRequestRepository implements ConnectorReplayRequestRepository {
  private static final String TRIGGER_TYPE_MANUAL = "MANUAL";
  private static final String STATUS_PENDING = "PENDING";

  private final JdbcTemplate jdbcTemplate;

  public JdbcConnectorReplayRequestRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public ConnectorReplaySubmission createManualReplayRequest(
      String connectorName, String reason, String requestedBy, Instant requestedAt) {
    Objects.requireNonNull(connectorName, "connectorName is required");
    Objects.requireNonNull(reason, "reason is required");
    Objects.requireNonNull(requestedBy, "requestedBy is required");
    Objects.requireNonNull(requestedAt, "requestedAt is required");

    UUID requestId = UUID.randomUUID();
    String sql =
        """
        INSERT INTO connector_replay_requests (
            id,
            connector_name,
            trigger_type,
            reason,
            status,
            requested_by,
            requested_at,
            created_at,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    jdbcTemplate.update(
        sql,
        requestId,
        connectorName,
        TRIGGER_TYPE_MANUAL,
        reason,
        STATUS_PENDING,
        requestedBy,
        requestedAt,
        requestedAt,
        requestedAt);
    return new ConnectorReplaySubmission(
        requestId, connectorName, STATUS_PENDING, requestedAt, requestedBy);
  }
}
