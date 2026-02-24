package com.tradingplatform.tradingapi.reconciliation;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcInternalBalanceSnapshotProvider implements InternalBalanceSnapshotProvider {
  private final JdbcTemplate jdbcTemplate;

  public JdbcInternalBalanceSnapshotProvider(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Map<String, BigDecimal> computeInternalTotals() {
    String sql =
        """
        SELECT asset, SUM(available + reserved) AS total
        FROM wallet_balances
        GROUP BY asset
        ORDER BY asset
        """;
    List<AssetTotalRow> rows = jdbcTemplate.query(sql, this::mapRow);
    Map<String, BigDecimal> totals = new LinkedHashMap<>();
    for (AssetTotalRow row : rows) {
      totals.put(row.asset(), row.total());
    }
    return totals;
  }

  private AssetTotalRow mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new AssetTotalRow(rs.getString("asset"), rs.getBigDecimal("total"));
  }

  private record AssetTotalRow(String asset, BigDecimal total) {}
}
