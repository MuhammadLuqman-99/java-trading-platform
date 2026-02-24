package com.tradingplatform.tradingapi.portfolio;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPortfolioReadRepository implements PortfolioReadRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcPortfolioReadRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public boolean accountExists(UUID accountId) {
    String sql = "SELECT EXISTS(SELECT 1 FROM accounts WHERE id = ?)";
    Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, accountId);
    return Boolean.TRUE.equals(exists);
  }

  @Override
  public List<WalletBalanceView> findBalancesByAccountId(UUID accountId) {
    String sql =
        """
        SELECT account_id, asset, available, reserved, updated_at
        FROM wallet_balances
        WHERE account_id = ?
        ORDER BY asset ASC
        """;
    return jdbcTemplate.query(sql, this::mapBalance, accountId);
  }

  @Override
  public List<PositionView> findPositionsByAccountId(UUID accountId) {
    String sql =
        """
        SELECT instrument, COALESCE(SUM(CASE WHEN side = 'BUY' THEN filled_qty ELSE -filled_qty END), 0) AS net_qty
        FROM orders
        WHERE account_id = ?
          AND filled_qty > 0
        GROUP BY instrument
        HAVING COALESCE(SUM(CASE WHEN side = 'BUY' THEN filled_qty ELSE -filled_qty END), 0) <> 0
        ORDER BY instrument ASC
        """;
    return jdbcTemplate.query(sql, this::mapPosition, accountId);
  }

  private WalletBalanceView mapBalance(ResultSet rs, int rowNum) throws SQLException {
    return new WalletBalanceView(
        rs.getObject("account_id", UUID.class),
        rs.getString("asset"),
        rs.getBigDecimal("available"),
        rs.getBigDecimal("reserved"),
        rs.getTimestamp("updated_at").toInstant());
  }

  private PositionView mapPosition(ResultSet rs, int rowNum) throws SQLException {
    return new PositionView(rs.getString("instrument"), rs.getBigDecimal("net_qty"));
  }
}
