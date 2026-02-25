package com.tradingplatform.integration.binance;

import com.tradingplatform.domain.orders.OrderStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcExchangeOrderStatusMappingRepository implements ExchangeOrderStatusMappingRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcExchangeOrderStatusMappingRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<ExchangeOrderStatusMapping> findAll() {
    String sql =
        """
        SELECT venue, external_status, domain_status, is_terminal, is_cancelable
        FROM exchange_order_status_mapping
        """;
    return jdbcTemplate.query(sql, this::mapRow);
  }

  private ExchangeOrderStatusMapping mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new ExchangeOrderStatusMapping(
        rs.getString("venue"),
        rs.getString("external_status"),
        OrderStatus.valueOf(rs.getString("domain_status")),
        rs.getBoolean("is_terminal"),
        rs.getBoolean("is_cancelable"));
  }
}
