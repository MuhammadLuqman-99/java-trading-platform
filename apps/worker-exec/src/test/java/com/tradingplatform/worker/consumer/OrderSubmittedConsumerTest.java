package com.tradingplatform.worker.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.contract.EventHeaders;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV2;
import com.tradingplatform.infra.kafka.errors.DeadLetterPublisher;
import com.tradingplatform.infra.kafka.errors.FixedBackoffRetryPolicy;
import com.tradingplatform.infra.kafka.observability.NoOpKafkaTelemetry;
import com.tradingplatform.infra.kafka.serde.EventEnvelopeJsonCodec;
import com.tradingplatform.infra.kafka.serde.EventObjectMapperFactory;
import com.tradingplatform.infra.kafka.topics.TopicNames;
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
  void shouldMapPayloadAndInvokeOrderSubmissionProcessor() {
    DeadLetterPublisher deadLetterPublisher = mock(DeadLetterPublisher.class);
    OrderSubmissionProcessor orderSubmissionProcessor = mock(OrderSubmissionProcessor.class);
    EventEnvelopeJsonCodec codec = new EventEnvelopeJsonCodec(EventObjectMapperFactory.create());
    OrderSubmittedConsumer consumer =
        new OrderSubmittedConsumer(
            codec,
            deadLetterPublisher,
            new FixedBackoffRetryPolicy(1, Duration.ZERO),
            new NoOpKafkaTelemetry(),
            orderSubmissionProcessor);

    EventEnvelope<OrderSubmittedV2> envelope = sampleEnvelope();
    ConsumerRecord<String, String> record = sampleRecord(codec.encode(envelope), envelope);
    Acknowledgment ack = mock(Acknowledgment.class);

    consumer.onMessage(record, ack);

    ArgumentCaptor<SubmitOrderCommand> commandCaptor = ArgumentCaptor.forClass(SubmitOrderCommand.class);
    verify(orderSubmissionProcessor).process(commandCaptor.capture());
    SubmitOrderCommand command = commandCaptor.getValue();
    assertEquals("6b8b4567-1234-4bba-a57c-f945f2999d01", command.orderId());
    assertEquals("6b8b4567-1234-4bba-a57c-f945f2999d02", command.accountId());
    assertEquals("BTCUSDT", command.instrument());
    assertEquals("BUY", command.side());
    assertEquals("LIMIT", command.type());
    assertEquals(new BigDecimal("0.01"), command.qty());
    assertEquals(new BigDecimal("40000.00"), command.price());
    assertEquals("client-1001", command.clientOrderId());
    assertEquals("corr-1001", command.correlationId());
    assertEquals(envelope.eventId(), command.eventId());
    verify(ack).acknowledge();
  }

  @Test
  void shouldDeadLetterWhenProcessorThrows() {
    DeadLetterPublisher deadLetterPublisher = mock(DeadLetterPublisher.class);
    OrderSubmissionProcessor orderSubmissionProcessor = mock(OrderSubmissionProcessor.class);
    whenThrowing(orderSubmissionProcessor);

    EventEnvelopeJsonCodec codec = new EventEnvelopeJsonCodec(EventObjectMapperFactory.create());
    OrderSubmittedConsumer consumer =
        new OrderSubmittedConsumer(
            codec,
            deadLetterPublisher,
            new FixedBackoffRetryPolicy(1, Duration.ZERO),
            new NoOpKafkaTelemetry(),
            orderSubmissionProcessor);

    EventEnvelope<OrderSubmittedV2> envelope = sampleEnvelope();
    ConsumerRecord<String, String> record = sampleRecord(codec.encode(envelope), envelope);
    Acknowledgment ack = mock(Acknowledgment.class);

    consumer.onMessage(record, ack);

    verify(deadLetterPublisher)
        .publish(
            eq(TopicNames.ORDERS_SUBMITTED_V2),
            eq(record),
            argThat(
                ex ->
                    ex instanceof RuntimeException
                        && "processor failure".equals(ex.getMessage())));
    verify(ack).acknowledge();
  }

  private static void whenThrowing(OrderSubmissionProcessor orderSubmissionProcessor) {
    org.mockito.Mockito.doThrow(new RuntimeException("processor failure"))
        .when(orderSubmissionProcessor)
        .process(org.mockito.ArgumentMatchers.any(SubmitOrderCommand.class));
  }

  private static EventEnvelope<OrderSubmittedV2> sampleEnvelope() {
    return EventEnvelope.of(
        EventTypes.ORDER_SUBMITTED,
        2,
        "worker-exec",
        "corr-1001",
        "6b8b4567-1234-4bba-a57c-f945f2999d01",
        new OrderSubmittedV2(
            "6b8b4567-1234-4bba-a57c-f945f2999d01",
            "6b8b4567-1234-4bba-a57c-f945f2999d02",
            "BTCUSDT",
            "BUY",
            "LIMIT",
            new BigDecimal("0.01"),
            new BigDecimal("40000.00"),
            "client-1001",
            Instant.parse("2026-02-24T12:00:00Z")));
  }

  private static ConsumerRecord<String, String> sampleRecord(
      String body, EventEnvelope<OrderSubmittedV2> envelope) {
    ConsumerRecord<String, String> record =
        new ConsumerRecord<>(
            TopicNames.ORDERS_SUBMITTED_V2,
            0,
            12L,
            "6b8b4567-1234-4bba-a57c-f945f2999d01",
            body);
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
