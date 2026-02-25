package com.tradingplatform.tradingapi.executions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExecutionReadRepository implements ExecutionReadRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcExecutionReadRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public boolean accountExists(UUID accountId) {
    String sql = "SELECT EXISTS(SELECT 1 FROM accounts WHERE id = ?)";
    Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, accountId);
    return Boolean.TRUE.equals(exists);
  }

  @Override
  public List<ExecutionView> findByAccountId(
      UUID accountId,
      UUID orderId,
      String symbol,
      Instant from,
      Instant to,
      int offset,
      int limit) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id,
                   order_id,
                   account_id,
                   instrument,
                   side,
                   trade_id,
                   exchange_name,
                   exchange_order_id,
                   qty,
                   price,
                   fee_asset,
                   fee_amount,
                   executed_at
            FROM executions
            WHERE account_id = ?
            """);
    List<Object> params = new ArrayList<>();
    params.add(accountId);
    appendFilters(sql, params, orderId, symbol, from, to);
    sql.append(" ORDER BY executed_at DESC, id DESC LIMIT ? OFFSET ?");
    params.add(limit);
    params.add(offset);
    return jdbcTemplate.query(sql.toString(), this::mapRow, params.toArray());
  }

  @Override
  public long countByAccountId(UUID accountId, UUID orderId, String symbol, Instant from, Instant to) {
    StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM executions WHERE account_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(accountId);
    appendFilters(sql, params, orderId, symbol, from, to);
    Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
    return count == null ? 0L : count;
  }

  private static void appendFilters(
      StringBuilder sql,
      List<Object> params,
      UUID orderId,
      String symbol,
      Instant from,
      Instant to) {
    if (orderId != null) {
      sql.append(" AND order_id = ?");
      params.add(orderId);
    }
    if (symbol != null) {
      sql.append(" AND instrument = ?");
      params.add(symbol);
    }
    if (from != null) {
      sql.append(" AND executed_at >= ?");
      params.add(Timestamp.from(from));
    }
    if (to != null) {
      sql.append(" AND executed_at <= ?");
      params.add(Timestamp.from(to));
    }
  }

  private ExecutionView mapRow(ResultSet rs, int rowNum) throws SQLException {
    Timestamp executedAt = rs.getTimestamp("executed_at");
    return new ExecutionView(
        rs.getObject("id", UUID.class),
        rs.getObject("order_id", UUID.class),
        rs.getObject("account_id", UUID.class),
        rs.getString("instrument"),
        rs.getString("side"),
        rs.getString("trade_id"),
        rs.getString("exchange_name"),
        rs.getString("exchange_order_id"),
        rs.getBigDecimal("qty"),
        rs.getBigDecimal("price"),
        rs.getString("fee_asset"),
        rs.getBigDecimal("fee_amount"),
        executedAt == null ? null : executedAt.toInstant());
  }
}
