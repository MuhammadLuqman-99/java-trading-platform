package com.tradingplatform.worker.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.contract.EventHeaders;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV1;
import com.tradingplatform.infra.kafka.errors.DeadLetterPublisher;
import com.tradingplatform.infra.kafka.errors.FixedBackoffRetryPolicy;
import com.tradingplatform.infra.kafka.observability.NoOpKafkaTelemetry;
import com.tradingplatform.infra.kafka.serde.EventEnvelopeJsonCodec;
import com.tradingplatform.infra.kafka.serde.EventObjectMapperFactory;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import com.tradingplatform.worker.execution.ExecutionOrderAdapter;
import com.tradingplatform.worker.execution.SubmitOrderCommand;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

class OrderSubmittedConsumerTest {
  @Test
  void shouldMapPayloadAndInvokeExecutionAdapter() {
    DeadLetterPublisher deadLetterPublisher = mock(DeadLetterPublisher.class);
    ExecutionOrderAdapter executionOrderAdapter = mock(ExecutionOrderAdapter.class);
    EventEnvelopeJsonCodec codec = new EventEnvelopeJsonCodec(EventObjectMapperFactory.create());
    OrderSubmittedConsumer consumer =
        new OrderSubmittedConsumer(
            codec,
            deadLetterPublisher,
            new FixedBackoffRetryPolicy(1, Duration.ZERO),
            new NoOpKafkaTelemetry(),
            executionOrderAdapter);

    EventEnvelope<OrderSubmittedV1> envelope = sampleEnvelope();
    ConsumerRecord<String, String> record = sampleRecord(codec.encode(envelope), envelope);
    Acknowledgment ack = mock(Acknowledgment.class);

    consumer.onMessage(record, ack);

    ArgumentCaptor<SubmitOrderCommand> commandCaptor = ArgumentCaptor.forClass(SubmitOrderCommand.class);
    verify(executionOrderAdapter).submitOrder(commandCaptor.capture());
    SubmitOrderCommand command = commandCaptor.getValue();
    assertEquals("ord-1001", command.orderId());
    assertEquals("acc-2001", command.accountId());
    assertEquals("BTCUSDT", command.instrument());
    assertEquals("BUY", command.side());
    assertEquals("LIMIT", command.type());
    assertEquals(new BigDecimal("0.01"), command.qty());
    assertEquals(new BigDecimal("40000.00"), command.price());
    assertEquals("ord-1001", command.correlationId());
    assertEquals(envelope.eventId(), command.eventId());
    verify(ack).acknowledge();
  }

  @Test
  void shouldDeadLetterWhenExecutionAdapterThrows() {
    DeadLetterPublisher deadLetterPublisher = mock(DeadLetterPublisher.class);
    ExecutionOrderAdapter executionOrderAdapter = mock(ExecutionOrderAdapter.class);
    whenThrowing(executionOrderAdapter);

    EventEnvelopeJsonCodec codec = new EventEnvelopeJsonCodec(EventObjectMapperFactory.create());
    OrderSubmittedConsumer consumer =
        new OrderSubmittedConsumer(
            codec,
            deadLetterPublisher,
            new FixedBackoffRetryPolicy(1, Duration.ZERO),
            new NoOpKafkaTelemetry(),
            executionOrderAdapter);

    EventEnvelope<OrderSubmittedV1> envelope = sampleEnvelope();
    ConsumerRecord<String, String> record = sampleRecord(codec.encode(envelope), envelope);
    Acknowledgment ack = mock(Acknowledgment.class);

    consumer.onMessage(record, ack);

    verify(deadLetterPublisher)
        .publish(
            eq(TopicNames.ORDERS_SUBMITTED_V1),
            eq(record),
            argThat(
                ex ->
                    ex instanceof RuntimeException
                        && "adapter failure".equals(ex.getMessage())));
    verify(ack).acknowledge();
  }

  private static void whenThrowing(ExecutionOrderAdapter executionOrderAdapter) {
    org.mockito.Mockito.doThrow(new RuntimeException("adapter failure"))
        .when(executionOrderAdapter)
        .submitOrder(org.mockito.ArgumentMatchers.any(SubmitOrderCommand.class));
  }

  private static EventEnvelope<OrderSubmittedV1> sampleEnvelope() {
    return EventEnvelope.of(
        EventTypes.ORDER_SUBMITTED,
        1,
        "worker-exec",
        "ord-1001",
        "ord-1001",
        new OrderSubmittedV1(
            "ord-1001",
            "acc-2001",
            "BTCUSDT",
            "BUY",
            "LIMIT",
            new BigDecimal("0.01"),
            new BigDecimal("40000.00"),
            Instant.parse("2026-02-24T12:00:00Z")));
  }

  private static ConsumerRecord<String, String> sampleRecord(
      String body, EventEnvelope<OrderSubmittedV1> envelope) {
    ConsumerRecord<String, String> record =
        new ConsumerRecord<>(TopicNames.ORDERS_SUBMITTED_V1, 0, 12L, "ord-1001", body);
    record
        .headers()
        .add(EventHeaders.X_EVENT_TYPE, envelope.eventType().getBytes(StandardCharsets.UTF_8));
    record
        .headers()
        .add(
            EventHeaders.X_EVENT_VERSION,
            Integer.toString(envelope.eventVersion()).getBytes(StandardCharsets.UTF_8));
    record
        .headers()
        .add(
            EventHeaders.X_CORRELATION_ID,
            envelope.correlationId().getBytes(StandardCharsets.UTF_8));
    return record;
  }
}
