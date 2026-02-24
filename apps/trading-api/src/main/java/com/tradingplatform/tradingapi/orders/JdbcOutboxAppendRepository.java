package com.tradingplatform.tradingapi.orders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.orders.Order;
import com.tradingplatform.domain.orders.OrderStatus;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV1;
import com.tradingplatform.infra.kafka.contract.payload.OrderUpdatedV1;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOutboxAppendRepository implements OutboxAppendRepository {
  private static final String AGGREGATE_TYPE = "ORDER";
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcOutboxAppendRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public void appendOrderSubmitted(Order order, String correlationId, Instant occurredAt) {
    OrderSubmittedV1 payload =
        new OrderSubmittedV1(
            order.id().toString(),
            order.accountId().toString(),
            order.instrument(),
            order.side().name(),
            order.type().name(),
            order.qty(),
            order.price(),
            occurredAt);
    append(
        order.id(),
        EventTypes.ORDER_SUBMITTED,
        payload,
        TopicNames.ORDERS_SUBMITTED_V1);
  }

  @Override
  public void appendOrderUpdated(
      Order order, OrderStatus fromStatus, String correlationId, Instant occurredAt) {
    OrderUpdatedV1 payload =
        new OrderUpdatedV1(
            order.id().toString(),
            order.accountId().toString(),
            order.status().name(),
            order.filledQty(),
            order.qty().subtract(order.filledQty()),
            order.exchangeOrderId(),
            occurredAt);
    append(order.id(), EventTypes.ORDER_UPDATED, payload, TopicNames.ORDERS_UPDATED_V1);
  }

  private void append(UUID orderId, String eventType, Object payload, String topic) {
    String payloadJson = toJson(payload);
    String sql =
        """
        INSERT INTO outbox_events (
            id,
            aggregate_type,
            aggregate_id,
            event_type,
            event_payload,
            topic,
            event_key,
            status,
            attempt_count,
            created_at
        ) VALUES (?, ?, ?, ?, CAST(? AS JSONB), ?, ?, 'NEW', 0, NOW())
        """;
    String key = orderId.toString();
    jdbcTemplate.update(sql, UUID.randomUUID(), AGGREGATE_TYPE, key, eventType, payloadJson, topic, key);
  }

  private String toJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize outbox payload", ex);
    }
  }
}
