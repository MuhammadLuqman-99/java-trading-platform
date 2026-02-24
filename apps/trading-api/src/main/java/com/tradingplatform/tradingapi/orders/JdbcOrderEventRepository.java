package com.tradingplatform.tradingapi.orders;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderEventRepository implements OrderEventRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcOrderEventRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void append(OrderEventAppend event) {
    String sql =
        """
        INSERT INTO order_events (
            id,
            order_id,
            event_type,
            from_status,
            to_status,
            payload_json,
            created_at
        ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), NOW())
        """;
    jdbcTemplate.update(
        sql,
        UUID.randomUUID(),
        event.orderId(),
        event.eventType(),
        event.fromStatus() == null ? null : event.fromStatus().name(),
        event.toStatus().name(),
        event.payloadJson());
  }
}
