package com.tradingplatform.worker.connector;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConnectorHealthRepository implements ConnectorHealthRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcConnectorHealthRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<ConnectorHealthState> findByConnectorName(String connectorName) {
    String sql =
        """
        SELECT connector_name,
               status,
               last_success_at,
               last_poll_started_at,
               last_poll_completed_at,
               last_error_at,
               last_error_code,
               last_error_message,
               open_orders_fetched,
               recent_trades_fetched,
               ws_connection_state,
               last_ws_connected_at,
               last_ws_disconnected_at,
               last_ws_error_at,
               last_ws_error_code,
               last_ws_error_message,
               ws_reconnect_attempts,
               updated_at
        FROM connector_health_state
        WHERE connector_name = ?
        """;
    List<ConnectorHealthState> rows = jdbcTemplate.query(sql, this::mapRow, connectorName);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rows.get(0));
  }

  @Override
  public void upsert(ConnectorHealthState state) {
    String sql =
        """
        INSERT INTO connector_health_state (
            connector_name,
            status,
            last_success_at,
            last_poll_started_at,
            last_poll_completed_at,
            last_error_at,
            last_error_code,
            last_error_message,
            open_orders_fetched,
            recent_trades_fetched,
            ws_connection_state,
            last_ws_connected_at,
            last_ws_disconnected_at,
            last_ws_error_at,
            last_ws_error_code,
            last_ws_error_message,
            ws_reconnect_attempts,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (connector_name) DO UPDATE SET
            status = EXCLUDED.status,
            last_success_at = EXCLUDED.last_success_at,
            last_poll_started_at = EXCLUDED.last_poll_started_at,
            last_poll_completed_at = EXCLUDED.last_poll_completed_at,
            last_error_at = EXCLUDED.last_error_at,
            last_error_code = EXCLUDED.last_error_code,
            last_error_message = EXCLUDED.last_error_message,
            open_orders_fetched = EXCLUDED.open_orders_fetched,
            recent_trades_fetched = EXCLUDED.recent_trades_fetched,
            ws_connection_state = EXCLUDED.ws_connection_state,
            last_ws_connected_at = EXCLUDED.last_ws_connected_at,
            last_ws_disconnected_at = EXCLUDED.last_ws_disconnected_at,
            last_ws_error_at = EXCLUDED.last_ws_error_at,
            last_ws_error_code = EXCLUDED.last_ws_error_code,
            last_ws_error_message = EXCLUDED.last_ws_error_message,
            ws_reconnect_attempts = EXCLUDED.ws_reconnect_attempts,
            updated_at = EXCLUDED.updated_at
        """;
    jdbcTemplate.update(
        sql,
        state.connectorName(),
        state.status().name(),
        state.lastSuccessAt(),
        state.lastPollStartedAt(),
        state.lastPollCompletedAt(),
        state.lastErrorAt(),
        state.lastErrorCode(),
        state.lastErrorMessage(),
        state.openOrdersFetched(),
        state.recentTradesFetched(),
        state.wsConnectionState().name(),
        state.lastWsConnectedAt(),
        state.lastWsDisconnectedAt(),
        state.lastWsErrorAt(),
        state.lastWsErrorCode(),
        state.lastWsErrorMessage(),
        state.wsReconnectAttempts(),
        state.updatedAt());
  }

  private ConnectorHealthState mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new ConnectorHealthState(
        rs.getString("connector_name"),
        ConnectorHealthStatus.valueOf(rs.getString("status")),
        toInstant(rs, "last_success_at"),
        toInstant(rs, "last_poll_started_at"),
        toInstant(rs, "last_poll_completed_at"),
        toInstant(rs, "last_error_at"),
        rs.getString("last_error_code"),
        rs.getString("last_error_message"),
        rs.getInt("open_orders_fetched"),
        rs.getInt("recent_trades_fetched"),
        ConnectorWsConnectionState.valueOf(rs.getString("ws_connection_state")),
        toInstant(rs, "last_ws_connected_at"),
        toInstant(rs, "last_ws_disconnected_at"),
        toInstant(rs, "last_ws_error_at"),
        rs.getString("last_ws_error_code"),
        rs.getString("last_ws_error_message"),
        rs.getLong("ws_reconnect_attempts"),
        toInstant(rs, "updated_at"));
  }

  private static Instant toInstant(ResultSet rs, String columnLabel) throws SQLException {
    java.sql.Timestamp timestamp = rs.getTimestamp(columnLabel);
    return timestamp == null ? null : timestamp.toInstant();
  }
}
