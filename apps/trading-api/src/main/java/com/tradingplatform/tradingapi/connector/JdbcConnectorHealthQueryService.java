package com.tradingplatform.tradingapi.connector;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class JdbcConnectorHealthQueryService implements ConnectorHealthQueryService {
  private final JdbcTemplate jdbcTemplate;

  public JdbcConnectorHealthQueryService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<ConnectorHealthSnapshot> findByConnectorName(String connectorName) {
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
               updated_at
        FROM connector_health_state
        WHERE connector_name = ?
        """;
    List<ConnectorHealthSnapshot> rows = jdbcTemplate.query(sql, this::mapRow, connectorName);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rows.get(0));
  }

  private ConnectorHealthSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new ConnectorHealthSnapshot(
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
        toInstant(rs, "updated_at"));
  }

  private static Instant toInstant(ResultSet rs, String columnLabel) throws SQLException {
    java.sql.Timestamp timestamp = rs.getTimestamp(columnLabel);
    return timestamp == null ? null : timestamp.toInstant();
  }
}
