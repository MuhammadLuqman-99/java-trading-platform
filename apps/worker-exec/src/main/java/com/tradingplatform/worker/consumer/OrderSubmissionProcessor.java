package com.tradingplatform.worker.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.OrderUpdatedV1;
import com.tradingplatform.infra.kafka.contract.payload.OrderUpdatedV2;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import com.tradingplatform.worker.execution.ExecutionAckResult;
import com.tradingplatform.worker.execution.ExecutionOrderAdapter;
import com.tradingplatform.worker.execution.SubmitOrderCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSubmissionProcessor {
  private static final String ORDER_AGGREGATE_TYPE = "ORDER";
  private static final String ORDER_STATUS_CHANGED_EVENT_TYPE = "ORDER_STATUS_CHANGED";
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final ExecutionOrderAdapter executionOrderAdapter;

  public OrderSubmissionProcessor(
      JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, ExecutionOrderAdapter executionOrderAdapter) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.executionOrderAdapter = executionOrderAdapter;
  }

  @Transactional
  public void process(SubmitOrderCommand command) {
    UUID orderId = parseUuid(command.orderId(), "orderId");
    UUID eventId = Objects.requireNonNull(command.eventId(), "eventId must not be null");

    if (!tryRecordProcessedEvent(eventId, orderId)) {
      return;
    }

    ExecutionAckResult ackResult = executionOrderAdapter.placeOrder(command);
    Instant now = Instant.now();
    String exchangeName = requireNonBlank(ackResult.exchangeName(), "exchangeName");
    String exchangeOrderId = requireNonBlank(ackResult.exchangeOrderId(), "exchangeOrderId");
    String exchangeClientOrderId =
        requireNonBlank(ackResult.exchangeClientOrderId(), "exchangeClientOrderId");

    OrderSnapshot snapshot = loadOrderForUpdate(orderId);
    if ("ACK".equals(snapshot.status())) {
      if (isSameAck(snapshot, exchangeName, exchangeOrderId, exchangeClientOrderId)) {
        return;
      }
      throw new IllegalStateException(
          "Order "
              + orderId
              + " already ACK with different exchange identifiers: "
              + snapshot.exchangeName()
              + "/"
              + snapshot.exchangeOrderId()
              + "/"
              + snapshot.exchangeClientOrderId());
    }
    if (!"NEW".equals(snapshot.status())) {
      throw new IllegalStateException(
          "Order " + orderId + " is not in NEW state; current status is " + snapshot.status());
    }

    updateToAck(orderId, exchangeName, exchangeOrderId, exchangeClientOrderId, now);
    appendOrderEvent(snapshot, exchangeName, exchangeOrderId, exchangeClientOrderId, now);
    appendOutboxEvents(snapshot, exchangeName, exchangeOrderId, exchangeClientOrderId, now);
  }

  private boolean tryRecordProcessedEvent(UUID eventId, UUID orderId) {
    String sql =
        """
        INSERT INTO processed_kafka_events (event_id, topic, order_id, processed_at)
        VALUES (?, ?, ?, NOW())
        ON CONFLICT (event_id) DO NOTHING
        """;
    int inserted = jdbcTemplate.update(sql, eventId, TopicNames.ORDERS_SUBMITTED_V2, orderId);
    return inserted == 1;
  }

  private OrderSnapshot loadOrderForUpdate(UUID orderId) {
    String sql =
        """
        SELECT id,
               account_id,
               status,
               qty,
               filled_qty,
               exchange_name,
               exchange_order_id,
               exchange_client_order_id
        FROM orders
        WHERE id = ?
        FOR UPDATE
        """;
    return jdbcTemplate.query(
        sql,
        rs -> {
          if (!rs.next()) {
            throw new IllegalStateException("Order not found for worker ACK update: " + orderId);
          }
          return new OrderSnapshot(
              rs.getObject("id", UUID.class),
              rs.getObject("account_id", UUID.class),
              rs.getString("status"),
              rs.getBigDecimal("qty"),
              rs.getBigDecimal("filled_qty"),
              rs.getString("exchange_name"),
              rs.getString("exchange_order_id"),
              rs.getString("exchange_client_order_id"));
        },
        orderId);
  }

  private void updateToAck(
      UUID orderId,
      String exchangeName,
      String exchangeOrderId,
      String exchangeClientOrderId,
      Instant now) {
    String sql =
        """
        UPDATE orders
        SET status = 'ACK',
            exchange_name = ?,
            exchange_order_id = ?,
            exchange_client_order_id = ?,
            updated_at = ?
        WHERE id = ?
          AND status = 'NEW'
        """;
    int updated = jdbcTemplate.update(sql, exchangeName, exchangeOrderId, exchangeClientOrderId, now, orderId);
    if (updated != 1) {
      throw new IllegalStateException(
          "Failed to transition order " + orderId + " to ACK because it is no longer NEW");
    }
  }

  private void appendOrderEvent(
      OrderSnapshot snapshot,
      String exchangeName,
      String exchangeOrderId,
      String exchangeClientOrderId,
      Instant occurredAt) {
    Map<String, Object> payload =
        Map.of(
            "reason", "exchange_ack",
            "filledQty", snapshot.filledQty(),
            "remainingQty", snapshot.qty().subtract(snapshot.filledQty()),
            "exchangeName", exchangeName,
            "exchangeOrderId", exchangeOrderId,
            "exchangeClientOrderId", exchangeClientOrderId,
            "occurredAt", occurredAt);
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
        snapshot.id(),
        ORDER_STATUS_CHANGED_EVENT_TYPE,
        snapshot.status(),
        "ACK",
        toJson(payload));
  }

  private void appendOutboxEvents(
      OrderSnapshot snapshot,
      String exchangeName,
      String exchangeOrderId,
      String exchangeClientOrderId,
      Instant occurredAt) {
    BigDecimal remainingQty = snapshot.qty().subtract(snapshot.filledQty());
    OrderUpdatedV1 payloadV1 =
        new OrderUpdatedV1(
            snapshot.id().toString(),
            snapshot.accountId().toString(),
            "ACK",
            snapshot.filledQty(),
            remainingQty,
            exchangeOrderId,
            occurredAt);
    appendOutboxEvent(snapshot.id(), TopicNames.ORDERS_UPDATED_V1, payloadV1);

    OrderUpdatedV2 payloadV2 =
        new OrderUpdatedV2(
            snapshot.id().toString(),
            snapshot.accountId().toString(),
            "ACK",
            snapshot.filledQty(),
            remainingQty,
            exchangeName,
            exchangeOrderId,
            exchangeClientOrderId,
            occurredAt);
    appendOutboxEvent(snapshot.id(), TopicNames.ORDERS_UPDATED_V2, payloadV2);
  }

  private void appendOutboxEvent(UUID orderId, String topic, Object payload) {
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
    jdbcTemplate.update(
        sql,
        UUID.randomUUID(),
        ORDER_AGGREGATE_TYPE,
        key,
        EventTypes.ORDER_UPDATED,
        toJson(payload),
        topic,
        key);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize worker payload", ex);
    }
  }

  private static boolean isSameAck(
      OrderSnapshot snapshot,
      String exchangeName,
      String exchangeOrderId,
      String exchangeClientOrderId) {
    return Objects.equals(snapshot.exchangeName(), exchangeName)
        && Objects.equals(snapshot.exchangeOrderId(), exchangeOrderId)
        && Objects.equals(snapshot.exchangeClientOrderId(), exchangeClientOrderId);
  }

  private static String requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(fieldName + " must not be blank");
    }
    return value;
  }

  private static UUID parseUuid(String value, String fieldName) {
    try {
      return UUID.fromString(value);
    } catch (Exception ex) {
      throw new IllegalStateException(fieldName + " must be a UUID: " + value, ex);
    }
  }

  private record OrderSnapshot(
      UUID id,
      UUID accountId,
      String status,
      BigDecimal qty,
      BigDecimal filledQty,
      String exchangeName,
      String exchangeOrderId,
      String exchangeClientOrderId) {}
}
