package com.tradingplatform.worker.connector;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcActiveInstrumentRepository implements ActiveInstrumentRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcActiveInstrumentRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<String> findActiveSymbols() {
    String sql =
        """
        SELECT symbol
        FROM instruments
        WHERE status = 'ACTIVE'
        ORDER BY symbol
        """;
    return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("symbol"));
  }
}
