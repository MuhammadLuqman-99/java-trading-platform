package com.tradingplatform.tradingapi.orders;

import com.tradingplatform.domain.orders.Order;
import com.tradingplatform.domain.orders.OrderSide;
import com.tradingplatform.domain.orders.OrderStatus;
import com.tradingplatform.domain.orders.OrderType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderRepository implements OrderRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcOrderRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void insert(Order order) {
    String sql =
        """
        INSERT INTO orders (
            id,
            account_id,
            instrument,
            side,
            type,
            qty,
            price,
            status,
            filled_qty,
            client_order_id,
            exchange_order_id,
            created_at,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    jdbcTemplate.update(
        sql,
        order.id(),
        order.accountId(),
        order.instrument(),
        order.side().name(),
        order.type().name(),
        order.qty(),
        order.price(),
        order.status().name(),
        order.filledQty(),
        order.clientOrderId(),
        order.exchangeOrderId(),
        order.createdAt(),
        order.updatedAt());
  }

  @Override
  public Optional<Order> findById(UUID orderId) {
    String sql =
        """
        SELECT id,
               account_id,
               instrument,
               side,
               type,
               qty,
               price,
               status,
               filled_qty,
               client_order_id,
               exchange_order_id,
               created_at,
               updated_at
        FROM orders
        WHERE id = ?
        """;
    List<Order> rows = jdbcTemplate.query(sql, this::mapRow, orderId);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rows.get(0));
  }

  @Override
  public void update(Order order) {
    String sql =
        """
        UPDATE orders
        SET status = ?,
            filled_qty = ?,
            exchange_order_id = ?,
            updated_at = ?
        WHERE id = ?
        """;
    jdbcTemplate.update(
        sql, order.status().name(), order.filledQty(), order.exchangeOrderId(), order.updatedAt(), order.id());
  }

  @Override
  public List<Order> findByAccountId(
      UUID accountId, String status, String instrument, int offset, int limit) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id, account_id, instrument, side, type, qty, price, status,
                   filled_qty, client_order_id, exchange_order_id, created_at, updated_at
            FROM orders
            WHERE account_id = ?
            """);
    List<Object> params = new ArrayList<>();
    params.add(accountId);
    if (status != null) {
      sql.append(" AND status = ?");
      params.add(status);
    }
    if (instrument != null) {
      sql.append(" AND instrument = ?");
      params.add(instrument);
    }
    sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
    params.add(limit);
    params.add(offset);
    return jdbcTemplate.query(sql.toString(), this::mapRow, params.toArray());
  }

  @Override
  public long countByAccountId(UUID accountId, String status, String instrument) {
    StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM orders WHERE account_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(accountId);
    if (status != null) {
      sql.append(" AND status = ?");
      params.add(status);
    }
    if (instrument != null) {
      sql.append(" AND instrument = ?");
      params.add(instrument);
    }
    Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
    return count != null ? count : 0L;
  }

  private Order mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new Order(
        rs.getObject("id", UUID.class),
        rs.getObject("account_id", UUID.class),
        rs.getString("instrument"),
        OrderSide.valueOf(rs.getString("side")),
        OrderType.valueOf(rs.getString("type")),
        rs.getBigDecimal("qty"),
        rs.getBigDecimal("price"),
        OrderStatus.valueOf(rs.getString("status")),
        rs.getBigDecimal("filled_qty"),
        rs.getString("client_order_id"),
        rs.getString("exchange_order_id"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
