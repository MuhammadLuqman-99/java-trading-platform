package com.tradingplatform.infra.kafka.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.contract.EventHeaders;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV1;
import com.tradingplatform.infra.kafka.errors.DeadLetterPublisher;
import com.tradingplatform.infra.kafka.errors.FixedBackoffRetryPolicy;
import com.tradingplatform.infra.kafka.errors.InvalidEventMetadataException;
import com.tradingplatform.infra.kafka.errors.RetryPolicy;
import com.tradingplatform.infra.kafka.errors.RetryableKafkaProcessingException;
import com.tradingplatform.infra.kafka.observability.NoOpKafkaTelemetry;
import com.tradingplatform.infra.kafka.serde.EventEnvelopeJsonCodec;
import com.tradingplatform.infra.kafka.serde.EventObjectMapperFactory;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class EventConsumerAdapterTest {
  private final EventEnvelopeJsonCodec codec =
      new EventEnvelopeJsonCodec(EventObjectMapperFactory.create());

  @Test
  void shouldRouteValidMessageToHandler() throws Exception {
    @SuppressWarnings("unchecked")
    EventHandler<OrderSubmittedV1> handler = mock(EventHandler.class);
    DeadLetterPublisher deadLetterPublisher = mock(DeadLetterPublisher.class);
    RetryPolicy retryPolicy = new FixedBackoffRetryPolicy(1, Duration.ZERO);
    EventConsumerAdapter<OrderSubmittedV1> adapter =
        new EventConsumerAdapter<>(
            OrderSubmittedV1.class,
            EventTypes.ORDER_SUBMITTED,
            1,
            codec,
            handler,
            deadLetterPublisher,
            retryPolicy,
            new NoOpKafkaTelemetry());

    ConsumerRecord<String, String> record = createRecord(true);
    adapter.process(record);

    verify(handler).handle(any());
    verify(deadLetterPublisher, never()).publish(any(), any(), any());
  }

  @Test
  void shouldDeadLetterWhenMetadataIsMissing() throws Exception {
    @SuppressWarnings("unchecked")
    EventHandler<OrderSubmittedV1> handler = mock(EventHandler.class);
    DeadLetterPublisher deadLetterPublisher = mock(DeadLetterPublisher.class);
    EventConsumerAdapter<OrderSubmittedV1> adapter =
        new EventConsumerAdapter<>(
            OrderSubmittedV1.class,
            EventTypes.ORDER_SUBMITTED,
            1,
            codec,
            handler,
            deadLetterPublisher,
            new FixedBackoffRetryPolicy(1, Duration.ZERO),
            new NoOpKafkaTelemetry());

    ConsumerRecord<String, String> record = createRecord(false);
    adapter.process(record);

    verify(handler, never()).handle(any());
    verify(deadLetterPublisher)
        .publish(
            eq(TopicNames.ORDERS_SUBMITTED_V1),
            eq(record),
            any(InvalidEventMetadataException.class));
  }

  @Test
  void shouldThrowRetryableExceptionWhenRetryPolicyAllowsRetry() throws Exception {
    @SuppressWarnings("unchecked")
    EventHandler<OrderSubmittedV1> handler = mock(EventHandler.class);
    DeadLetterPublisher deadLetterPublisher = mock(DeadLetterPublisher.class);
    EventConsumerAdapter<OrderSubmittedV1> adapter =
        new EventConsumerAdapter<>(
            OrderSubmittedV1.class,
            EventTypes.ORDER_SUBMITTED,
            1,
            codec,
            handler,
            deadLetterPublisher,
            new FixedBackoffRetryPolicy(2, Duration.ofMillis(50)),
            new NoOpKafkaTelemetry());
    ConsumerRecord<String, String> record = createRecord(true);

    org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(handler).handle(any());

    assertThrows(RetryableKafkaProcessingException.class, () -> adapter.process(record, 1));
    verify(deadLetterPublisher, never()).publish(any(), any(), any());
  }

  @Test
  void shouldDeadLetterWhenHandlerFailsAndNoRetryConfigured() throws Exception {
    @SuppressWarnings("unchecked")
    EventHandler<OrderSubmittedV1> handler = mock(EventHandler.class);
    DeadLetterPublisher deadLetterPublisher = mock(DeadLetterPublisher.class);
    EventConsumerAdapter<OrderSubmittedV1> adapter =
        new EventConsumerAdapter<>(
            OrderSubmittedV1.class,
            EventTypes.ORDER_SUBMITTED,
            1,
            codec,
            handler,
            deadLetterPublisher,
            new FixedBackoffRetryPolicy(1, Duration.ZERO),
            new NoOpKafkaTelemetry());
    ConsumerRecord<String, String> record = createRecord(true);

    org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(handler).handle(any());
    adapter.process(record, 1);

    verify(deadLetterPublisher)
        .publish(eq(TopicNames.ORDERS_SUBMITTED_V1), eq(record), any(IllegalStateException.class));
  }

  private ConsumerRecord<String, String> createRecord(boolean includeVersionHeader) {
    EventEnvelope<OrderSubmittedV1> envelope =
        EventEnvelope.of(
            EventTypes.ORDER_SUBMITTED,
            1,
            "worker-test",
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
    String json = codec.encode(envelope);

    ConsumerRecord<String, String> record =
        new ConsumerRecord<>(TopicNames.ORDERS_SUBMITTED_V1, 0, 0L, "ord-1001", json);
    var headers = record.headers();
    headers.add(
        EventHeaders.X_EVENT_TYPE, EventTypes.ORDER_SUBMITTED.getBytes(StandardCharsets.UTF_8));
    headers.add(EventHeaders.X_CORRELATION_ID, "ord-1001".getBytes(StandardCharsets.UTF_8));
    headers.add(
        EventHeaders.CONTENT_TYPE, EventHeaders.APPLICATION_JSON.getBytes(StandardCharsets.UTF_8));
    if (includeVersionHeader) {
      headers.add(EventHeaders.X_EVENT_VERSION, "1".getBytes(StandardCharsets.UTF_8));
    }

    return record;
  }
}
