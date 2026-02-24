package com.tradingplatform.infra.kafka.producer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.contract.EventHeaders;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV1;
import com.tradingplatform.infra.kafka.observability.NoOpKafkaTelemetry;
import com.tradingplatform.infra.kafka.serde.EventEnvelopeJsonCodec;
import com.tradingplatform.infra.kafka.serde.EventObjectMapperFactory;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaEventPublisherTest {
  @Test
  void shouldPublishWithRequiredHeadersAndKey() throws Exception {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    EventEnvelopeJsonCodec codec = new EventEnvelopeJsonCodec(EventObjectMapperFactory.create());
    KafkaEventPublisher publisher =
        new KafkaEventPublisher(kafkaTemplate, codec, new NoOpKafkaTelemetry());

    ProducerRecord<String, String> mockedResultRecord =
        new ProducerRecord<>(TopicNames.ORDERS_SUBMITTED_V1, "ord-1001", "{}");
    CompletableFuture<SendResult<String, String>> sendFuture =
        CompletableFuture.completedFuture(new SendResult<>(mockedResultRecord, null));
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(sendFuture);

    EventEnvelope<OrderSubmittedV1> envelope =
        EventEnvelope.of(
            EventTypes.ORDER_SUBMITTED,
            1,
            "trading-api",
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

    publisher.publish(TopicNames.ORDERS_SUBMITTED_V1, "ord-1001", envelope).get();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<ProducerRecord<String, String>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(captor.capture());
    ProducerRecord<String, String> actualRecord = captor.getValue();

    assertEquals(TopicNames.ORDERS_SUBMITTED_V1, actualRecord.topic());
    assertEquals("ord-1001", actualRecord.key());
    assertNotNull(actualRecord.value());
    assertEquals(EventTypes.ORDER_SUBMITTED, headerValue(actualRecord, EventHeaders.X_EVENT_TYPE));
    assertEquals("1", headerValue(actualRecord, EventHeaders.X_EVENT_VERSION));
    assertEquals("ord-1001", headerValue(actualRecord, EventHeaders.X_CORRELATION_ID));
    assertEquals(
        EventHeaders.APPLICATION_JSON, headerValue(actualRecord, EventHeaders.CONTENT_TYPE));
  }

  private static String headerValue(ProducerRecord<String, String> record, String headerName) {
    Header header = record.headers().lastHeader(headerName);
    assertNotNull(header, "Expected header " + headerName + " to exist");
    return new String(header.value(), StandardCharsets.UTF_8);
  }

  @Test
  void shouldWrapPublishFailureWithKafkaPublishException() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    EventEnvelopeJsonCodec codec = new EventEnvelopeJsonCodec(EventObjectMapperFactory.create());
    KafkaEventPublisher publisher =
        new KafkaEventPublisher(kafkaTemplate, codec, new NoOpKafkaTelemetry(), Duration.ZERO);

    CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new IllegalStateException("broker unavailable"));
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture);

    EventEnvelope<OrderSubmittedV1> envelope =
        EventEnvelope.of(
            EventTypes.ORDER_SUBMITTED,
            1,
            "trading-api",
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

    ExecutionException ex =
        assertThrows(
            ExecutionException.class,
            () -> publisher.publish(TopicNames.ORDERS_SUBMITTED_V1, "ord-1001", envelope).get());
    assertEquals(KafkaPublishException.class, ex.getCause().getClass());
  }
}
