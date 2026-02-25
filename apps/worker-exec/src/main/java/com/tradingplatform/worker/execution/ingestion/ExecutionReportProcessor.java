package com.tradingplatform.worker.execution.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.orders.OrderStateMachine;
import com.tradingplatform.domain.orders.OrderStatus;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.ExecutionRecordedV1;
import com.tradingplatform.infra.kafka.contract.payload.OrderUpdatedV1;
import com.tradingplatform.infra.kafka.contract.payload.OrderUpdatedV2;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import com.tradingplatform.integration.binance.BinanceExecutionReport;
import com.tradingplatform.integration.binance.BinanceVenue;
import com.tradingplatform.integration.binance.ExchangeOrderStatusMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionReportProcessor {
  private static final String ORDER_AGGREGATE_TYPE = "ORDER";
  private static final String ORDER_STATUS_CHANGED_EVENT_TYPE = "ORDER_STATUS_CHANGED";
  private static final String ORDER_EXCHANGE_NAME = "BINANCE";

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final ExchangeOrderStatusMapper exchangeOrderStatusMapper;
  private final ExecutionRepository executionRepository;

  public ExecutionReportProcessor(
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      ExchangeOrderStatusMapper exchangeOrderStatusMapper,
      ExecutionRepository executionRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.exchangeOrderStatusMapper = exchangeOrderStatusMapper;
    this.executionRepository = executionRepository;
  }

  @Transactional
  public ExecutionIngestionResult process(BinanceExecutionReport report) {
    OrderSnapshot order = loadOrderForUpdate(report.exchangeOrderId(), report.exchangeClientOrderId());

    UUID executionId = UUID.randomUUID();
    boolean inserted =
        executionRepository.insertIfAbsent(
            new ExecutionInsert(
                executionId,
                order.id(),
                order.accountId(),
                order.instrument(),
                report.exchangeTradeId(),
                ORDER_EXCHANGE_NAME,
                report.exchangeOrderId(),
                order.side(),
                report.lastExecutedQty(),
                report.lastExecutedPrice(),
                report.feeAsset(),
                report.feeAmount(),
                report.tradeTime()));
    if (!inserted) {
      return ExecutionIngestionResult.DUPLICATE;
    }

    BigDecimal nextFilledQty = order.filledQty().max(report.cumulativeExecutedQty());
    if (nextFilledQty.compareTo(order.qty()) > 0) {
      throw new IllegalStateException(
          "Execution cumulative quantity "
              + nextFilledQty
              + " exceeds order qty "
              + order.qty()
              + " for order "
              + order.id());
    }

    OrderStatus mappedStatus =
        exchangeOrderStatusMapper.toDomainStatus(
            BinanceVenue.BINANCE_SPOT, report.externalOrderStatus());
    OrderStatus nextStatus = resolveNextStatus(order.status(), mappedStatus, nextFilledQty, order.qty());
    Instant occurredAt = report.tradeTime();

    updateOrder(order.id(), nextStatus, nextFilledQty, report.exchangeOrderId(), report.exchangeClientOrderId(), occurredAt);
    appendOrderEvent(order, nextStatus, nextFilledQty, report, occurredAt);
    appendOrderUpdatedOutbox(order, nextStatus, nextFilledQty, report, occurredAt);
    appendExecutionRecordedOutbox(executionId, order, report);
    return ExecutionIngestionResult.PROCESSED;
  }

  private OrderSnapshot loadOrderForUpdate(String exchangeOrderId, String exchangeClientOrderId) {
    List<OrderSnapshot> byExchangeOrderId =
        findOrderForUpdate(
            """
            SELECT id, account_id, instrument, side, status, qty, filled_qty, exchange_name, exchange_order_id, exchange_client_order_id
            FROM orders
            WHERE exchange_name = ?
              AND exchange_order_id = ?
            FOR UPDATE
            """,
            ORDER_EXCHANGE_NAME,
            exchangeOrderId);
    if (!byExchangeOrderId.isEmpty()) {
      return byExchangeOrderId.get(0);
    }

    List<OrderSnapshot> byExchangeClientOrderId =
        findOrderForUpdate(
            """
            SELECT id, account_id, instrument, side, status, qty, filled_qty, exchange_name, exchange_order_id, exchange_client_order_id
            FROM orders
            WHERE exchange_name = ?
              AND exchange_client_order_id = ?
            FOR UPDATE
            """,
            ORDER_EXCHANGE_NAME,
            exchangeClientOrderId);
    if (!byExchangeClientOrderId.isEmpty()) {
      return byExchangeClientOrderId.get(0);
    }
    throw new IllegalStateException(
        "Order not found for execution report exchangeOrderId="
            + exchangeOrderId
            + " exchangeClientOrderId="
            + exchangeClientOrderId);
  }

  private List<OrderSnapshot> findOrderForUpdate(String sql, Object... args) {
    List<OrderSnapshot> rows = jdbcTemplate.query(sql, this::mapOrderSnapshot, args);
    if (rows.size() > 1) {
      throw new IllegalStateException("Expected one order match but found " + rows.size());
    }
    return rows;
  }

  private OrderSnapshot mapOrderSnapshot(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    return new OrderSnapshot(
        rs.getObject("id", UUID.class),
        rs.getObject("account_id", UUID.class),
        rs.getString("instrument"),
        rs.getString("side"),
        OrderStatus.valueOf(rs.getString("status")),
        rs.getBigDecimal("qty"),
        rs.getBigDecimal("filled_qty"),
        rs.getString("exchange_name"),
        rs.getString("exchange_order_id"),
        rs.getString("exchange_client_order_id"));
  }

  private static OrderStatus resolveNextStatus(
      OrderStatus currentStatus,
      OrderStatus mappedStatus,
      BigDecimal nextFilledQty,
      BigDecimal orderQty) {
    OrderStatus quantityDrivenStatus = null;
    if (nextFilledQty.compareTo(orderQty) == 0) {
      quantityDrivenStatus = OrderStatus.FILLED;
    } else if (nextFilledQty.compareTo(BigDecimal.ZERO) > 0) {
      quantityDrivenStatus = OrderStatus.PARTIALLY_FILLED;
    }

    OrderStatus candidate = mappedStatus;
    if (quantityDrivenStatus == OrderStatus.FILLED) {
      candidate = OrderStatus.FILLED;
    } else if (quantityDrivenStatus == OrderStatus.PARTIALLY_FILLED
        && mappedStatus != OrderStatus.FILLED) {
      candidate = OrderStatus.PARTIALLY_FILLED;
    }

    if (candidate == currentStatus) {
      return candidate;
    }
    if (OrderStateMachine.canTransition(currentStatus, candidate)) {
      return candidate;
    }
    if (quantityDrivenStatus != null && OrderStateMachine.canTransition(currentStatus, quantityDrivenStatus)) {
      return quantityDrivenStatus;
    }
    throw new IllegalStateException(
        "Invalid execution-driven transition from "
            + currentStatus
            + " to "
            + candidate
            + " mappedStatus="
            + mappedStatus
            + " nextFilledQty="
            + nextFilledQty
            + " orderQty="
            + orderQty);
  }

  private void updateOrder(
      UUID orderId,
      OrderStatus nextStatus,
      BigDecimal nextFilledQty,
      String exchangeOrderId,
      String exchangeClientOrderId,
      Instant updatedAt) {
    String sql =
        """
        UPDATE orders
        SET status = ?,
            filled_qty = ?,
            exchange_name = ?,
            exchange_order_id = ?,
            exchange_client_order_id = ?,
            updated_at = ?
        WHERE id = ?
        """;
    int updated =
        jdbcTemplate.update(
            sql,
            nextStatus.name(),
            nextFilledQty,
            ORDER_EXCHANGE_NAME,
            exchangeOrderId,
            exchangeClientOrderId,
            updatedAt,
            orderId);
    if (updated != 1) {
      throw new IllegalStateException("Failed to update order " + orderId + " for execution ingestion");
    }
  }

  private void appendOrderEvent(
      OrderSnapshot order,
      OrderStatus nextStatus,
      BigDecimal nextFilledQty,
      BinanceExecutionReport report,
      Instant occurredAt) {
    Map<String, Object> payload =
        Map.of(
            "reason", "execution_report_trade",
            "tradeId", report.exchangeTradeId(),
            "filledQty", nextFilledQty,
            "remainingQty", order.qty().subtract(nextFilledQty),
            "exchangeName", ORDER_EXCHANGE_NAME,
            "exchangeOrderId", report.exchangeOrderId(),
            "exchangeClientOrderId", report.exchangeClientOrderId(),
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
        order.id(),
        ORDER_STATUS_CHANGED_EVENT_TYPE,
        order.status().name(),
        nextStatus.name(),
        toJson(payload));
  }

  private void appendOrderUpdatedOutbox(
      OrderSnapshot order,
      OrderStatus nextStatus,
      BigDecimal nextFilledQty,
      BinanceExecutionReport report,
      Instant occurredAt) {
    BigDecimal remainingQty = order.qty().subtract(nextFilledQty);
    OrderUpdatedV1 orderUpdatedV1 =
        new OrderUpdatedV1(
            order.id().toString(),
            order.accountId().toString(),
            nextStatus.name(),
            nextFilledQty,
            remainingQty,
            report.exchangeOrderId(),
            occurredAt);
    appendOutboxEvent(order.id(), EventTypes.ORDER_UPDATED, orderUpdatedV1, TopicNames.ORDERS_UPDATED_V1);

    OrderUpdatedV2 orderUpdatedV2 =
        new OrderUpdatedV2(
            order.id().toString(),
            order.accountId().toString(),
            nextStatus.name(),
            nextFilledQty,
            remainingQty,
            ORDER_EXCHANGE_NAME,
            report.exchangeOrderId(),
            report.exchangeClientOrderId(),
            occurredAt);
    appendOutboxEvent(order.id(), EventTypes.ORDER_UPDATED, orderUpdatedV2, TopicNames.ORDERS_UPDATED_V2);
  }

  private void appendExecutionRecordedOutbox(
      UUID executionId, OrderSnapshot order, BinanceExecutionReport report) {
    ExecutionRecordedV1 payload =
        new ExecutionRecordedV1(
            executionId.toString(),
            order.id().toString(),
            order.accountId().toString(),
            report.exchangeTradeId(),
            report.lastExecutedQty(),
            report.lastExecutedPrice(),
            report.feeAsset(),
            report.feeAmount(),
            report.tradeTime());
    appendOutboxEvent(
        order.id(), EventTypes.EXECUTION_RECORDED, payload, TopicNames.EXECUTIONS_RECORDED_V1);
  }

  private void appendOutboxEvent(UUID aggregateId, String eventType, Object payload, String topic) {
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
    String key = aggregateId.toString();
    jdbcTemplate.update(
        sql,
        UUID.randomUUID(),
        ORDER_AGGREGATE_TYPE,
        key,
        eventType,
        toJson(payload),
        topic,
        key);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize payload", ex);
    }
  }

  private record OrderSnapshot(
      UUID id,
      UUID accountId,
      String instrument,
      String side,
      OrderStatus status,
      BigDecimal qty,
      BigDecimal filledQty,
      String exchangeName,
      String exchangeOrderId,
      String exchangeClientOrderId) {}
}
