package com.tradingplatform.worker.execution.ingestion;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExecutionRepository implements ExecutionRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcExecutionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public boolean insertIfAbsent(ExecutionInsert execution) {
    String sql =
        """
        INSERT INTO executions (
            id,
            order_id,
            account_id,
            instrument,
            trade_id,
            exchange_name,
            exchange_order_id,
            side,
            qty,
            price,
            fee_asset,
            fee_amount,
            executed_at,
            created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
        ON CONFLICT (exchange_name, instrument, trade_id) DO NOTHING
        RETURNING id
        """;
    List<UUID> inserted =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            execution.id(),
            execution.orderId(),
            execution.accountId(),
            execution.instrument(),
            execution.tradeId(),
            execution.exchangeName(),
            execution.exchangeOrderId(),
            execution.side(),
            execution.qty(),
            execution.price(),
            execution.feeAsset(),
            execution.feeAmount(),
            execution.executedAt());
    return !inserted.isEmpty();
  }
}
