package com.tradingplatform.worker.connector;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConnectorReplayRequestRepository implements ConnectorReplayRequestRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcConnectorReplayRequestRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<ConnectorReplayRequest> claimNextPending(String connectorName, Instant claimedAt) {
    String sql =
        """
        WITH next_request AS (
            SELECT id
            FROM connector_replay_requests
            WHERE connector_name = ?
              AND status = 'PENDING'
            ORDER BY requested_at
            FOR UPDATE SKIP LOCKED
            LIMIT 1
        )
        UPDATE connector_replay_requests request
        SET status = 'RUNNING',
            started_at = ?,
            updated_at = ?
        FROM next_request
        WHERE request.id = next_request.id
        RETURNING request.id,
                  request.connector_name,
                  request.trigger_type,
                  request.reason,
                  request.status,
                  request.requested_by,
                  request.requested_at,
                  request.started_at,
                  request.completed_at,
                  request.error_code,
                  request.error_message
        """;
    List<ConnectorReplayRequest> rows =
        jdbcTemplate.query(sql, this::mapRow, connectorName, claimedAt, claimedAt);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rows.get(0));
  }

  @Override
  public void markSucceeded(UUID requestId, Instant completedAt) {
    String sql =
        """
        UPDATE connector_replay_requests
        SET status = 'SUCCEEDED',
            completed_at = ?,
            error_code = NULL,
            error_message = NULL,
            updated_at = ?
        WHERE id = ?
        """;
    jdbcTemplate.update(sql, completedAt, completedAt, requestId);
  }

  @Override
  public void markFailed(
      UUID requestId, String errorCode, String errorMessage, Instant completedAt) {
    String sql =
        """
        UPDATE connector_replay_requests
        SET status = 'FAILED',
            completed_at = ?,
            error_code = ?,
            error_message = ?,
            updated_at = ?
        WHERE id = ?
        """;
    jdbcTemplate.update(sql, completedAt, errorCode, errorMessage, completedAt, requestId);
  }

  @Override
  public boolean enqueueRecoveryIfWindowClear(
      String connectorName,
      String reason,
      String requestedBy,
      Instant requestedAt,
      Duration dedupeWindow) {
    long dedupeMs = Math.max(0L, dedupeWindow == null ? 0L : dedupeWindow.toMillis());
    Instant windowStart = requestedAt.minusMillis(dedupeMs);
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
        )
        SELECT ?, ?, 'RECOVERY', ?, 'PENDING', ?, ?, ?, ?
        WHERE NOT EXISTS (
            SELECT 1
            FROM connector_replay_requests
            WHERE connector_name = ?
              AND trigger_type = 'RECOVERY'
              AND requested_at >= ?
        )
        """;
    int inserted =
        jdbcTemplate.update(
            sql,
            requestId,
            connectorName,
            reason,
            requestedBy,
            requestedAt,
            requestedAt,
            requestedAt,
            connectorName,
            windowStart);
    return inserted == 1;
  }

  @Override
  public int countPending(String connectorName) {
    String sql =
        """
        SELECT COUNT(*)
        FROM connector_replay_requests
        WHERE connector_name = ?
          AND status = 'PENDING'
        """;
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, connectorName);
    return count == null ? 0 : count;
  }

  private ConnectorReplayRequest mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new ConnectorReplayRequest(
        rs.getObject("id", UUID.class),
        rs.getString("connector_name"),
        ConnectorReplayTriggerType.valueOf(rs.getString("trigger_type")),
        rs.getString("reason"),
        ConnectorReplayRequestStatus.valueOf(rs.getString("status")),
        rs.getString("requested_by"),
        toInstant(rs, "requested_at"),
        toInstant(rs, "started_at"),
        toInstant(rs, "completed_at"),
        rs.getString("error_code"),
        rs.getString("error_message"));
  }

  private static Instant toInstant(ResultSet rs, String columnLabel) throws SQLException {
    java.sql.Timestamp timestamp = rs.getTimestamp(columnLabel);
    return timestamp == null ? null : timestamp.toInstant();
  }
}
