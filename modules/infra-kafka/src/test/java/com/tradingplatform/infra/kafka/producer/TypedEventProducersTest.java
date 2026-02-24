package com.tradingplatform.infra.kafka.producer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.BalanceUpdatedV1;
import com.tradingplatform.infra.kafka.contract.payload.ExecutionRecordedV1;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV1;
import com.tradingplatform.infra.kafka.contract.payload.OrderUpdatedV1;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.SendResult;

class TypedEventProducersTest {
  @Test
  void shouldPublishOrderSubmittedUsingStandardTopicAndEnvelope() {
    EventPublisher eventPublisher = mock(EventPublisher.class);
    CompletableFuture<SendResult<String, String>> sendFuture = CompletableFuture.completedFuture(null);
    when(eventPublisher.publish(anyString(), anyString(), any())).thenReturn(sendFuture);

    OrderEventProducer producer = new OrderEventProducer(eventPublisher, "worker-exec");
    OrderSubmittedV1 payload =
        new OrderSubmittedV1(
            "ord-1",
            "acc-1",
            "BTCUSDT",
            "BUY",
            "LIMIT",
            new BigDecimal("0.10"),
            new BigDecimal("50000.00"),
            Instant.parse("2026-02-24T12:00:00Z"));

    producer.publishOrderSubmitted(payload);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<EventEnvelope<OrderSubmittedV1>> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
    verify(eventPublisher)
        .publish(anyString(), anyString(), envelopeCaptor.capture());
    EventEnvelope<OrderSubmittedV1> envelope = envelopeCaptor.getValue();

    assertEquals(EventTypes.ORDER_SUBMITTED, envelope.eventType());
    assertEquals(1, envelope.eventVersion());
    assertEquals("worker-exec", envelope.producer());
    assertEquals("ord-1", envelope.key());
    assertEquals(payload, envelope.payload());
  }

  @Test
  void shouldPublishOrderUpdatedUsingOrdersUpdatedTopic() {
    EventPublisher eventPublisher = mock(EventPublisher.class);
    CompletableFuture<SendResult<String, String>> sendFuture = CompletableFuture.completedFuture(null);
    when(eventPublisher.publish(anyString(), anyString(), any())).thenReturn(sendFuture);

    OrderEventProducer producer = new OrderEventProducer(eventPublisher, "worker-exec");
    OrderUpdatedV1 payload =
        new OrderUpdatedV1(
            "ord-2",
            "acc-2",
            "ACK",
            new BigDecimal("0.01"),
            new BigDecimal("0.09"),
            "ex-1",
            Instant.parse("2026-02-24T12:01:00Z"));

    producer.publishOrderUpdated(payload);

    verify(eventPublisher).publish(eq(TopicNames.ORDERS_UPDATED_V1), eq("ord-2"), any());
  }

  @Test
  void shouldPublishExecutionAndBalanceUsingStandardTopics() {
    EventPublisher eventPublisher = mock(EventPublisher.class);
    CompletableFuture<SendResult<String, String>> sendFuture = CompletableFuture.completedFuture(null);
    when(eventPublisher.publish(anyString(), anyString(), any())).thenReturn(sendFuture);

    ExecutionEventProducer executionProducer = new ExecutionEventProducer(eventPublisher, "worker-exec");
    executionProducer.publishExecutionRecorded(
        new ExecutionRecordedV1(
            "exec-1",
            "ord-3",
            "acc-3",
            "trade-1",
            new BigDecimal("0.01"),
            new BigDecimal("52000"),
            "USDT",
            new BigDecimal("0.10"),
            Instant.parse("2026-02-24T12:02:00Z")));
    verify(eventPublisher).publish(eq(TopicNames.EXECUTIONS_RECORDED_V1), eq("ord-3"), any());

    BalanceEventProducer balanceProducer = new BalanceEventProducer(eventPublisher, "worker-exec");
    balanceProducer.publishBalanceUpdated(
        new BalanceUpdatedV1(
            "acc-4",
            "USDT",
            new BigDecimal("1000"),
            new BigDecimal("10"),
            "ORDER_FILLED",
            Instant.parse("2026-02-24T12:03:00Z")));
    verify(eventPublisher).publish(eq(TopicNames.BALANCES_UPDATED_V1), eq("acc-4"), any());
  }

  @Test
  void shouldRejectBlankKeyFields() {
    EventPublisher eventPublisher = mock(EventPublisher.class);
    OrderEventProducer producer = new OrderEventProducer(eventPublisher, "worker-exec");
    OrderSubmittedV1 payload =
        new OrderSubmittedV1(
            "  ",
            "acc-1",
            "BTCUSDT",
            "BUY",
            "LIMIT",
            new BigDecimal("0.10"),
            new BigDecimal("50000.00"),
            Instant.parse("2026-02-24T12:00:00Z"));

    assertThrows(IllegalArgumentException.class, () -> producer.publishOrderSubmitted(payload));
  }
}
