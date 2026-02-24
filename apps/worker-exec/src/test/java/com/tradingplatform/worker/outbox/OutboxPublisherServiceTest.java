package com.tradingplatform.worker.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.producer.EventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {
  @Mock private OutboxRepository outboxRepository;

  @Mock private EventPublisher eventPublisher;

  private OutboxPublisherProperties properties;
  private OutboxPublisherService service;

  @BeforeEach
  void setUp() {
    properties = new OutboxPublisherProperties();
    properties.setBatchSize(50);
    properties.setProducerName("worker-exec-outbox-publisher");
    service =
        new OutboxPublisherService(
            outboxRepository, eventPublisher, properties, new ObjectMapper());
  }

  @Test
  void shouldPublishPendingEventAndMarkPublished() {
    UUID outboxId = UUID.randomUUID();
    OutboxEventRecord record =
        new OutboxEventRecord(
            outboxId,
            "ORDER",
            "ord-1001",
            "OrderSubmitted",
            "{\"orderId\":\"ord-1001\"}",
            "orders.submitted.v1",
            "ord-1001",
            "NEW",
            0,
            Instant.parse("2026-02-24T12:00:00Z"));

    when(outboxRepository.findPendingBatch(50)).thenReturn(List.of(record));
    CompletableFuture<SendResult<String, String>> successFuture =
        CompletableFuture.completedFuture(null);
    when(eventPublisher.publish(
            eq("orders.submitted.v1"), eq("ord-1001"), any(EventEnvelope.class)))
        .thenReturn(successFuture);

    service.publishPendingEvents();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<EventEnvelope<?>> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
    verify(eventPublisher)
        .publish(eq("orders.submitted.v1"), eq("ord-1001"), envelopeCaptor.capture());
    EventEnvelope<?> envelope = envelopeCaptor.getValue();
    assertEquals("OrderSubmitted", envelope.eventType());
    assertEquals(1, envelope.eventVersion());
    assertEquals("ord-1001", envelope.correlationId());
    assertEquals("ord-1001", envelope.key());
    assertNotNull(envelope.payload());

    verify(outboxRepository).markPublished(eq(outboxId), any(Instant.class));
    verify(outboxRepository, never()).markFailed(eq(outboxId), any());
  }

  @Test
  void shouldMarkFailedWhenPublishThrows() {
    UUID outboxId = UUID.randomUUID();
    OutboxEventRecord record =
        new OutboxEventRecord(
            outboxId,
            "ORDER",
            "ord-1002",
            "OrderSubmitted",
            "{\"orderId\":\"ord-1002\"}",
            "orders.submitted.v1",
            "ord-1002",
            "NEW",
            1,
            Instant.parse("2026-02-24T12:01:00Z"));

    when(outboxRepository.findPendingBatch(50)).thenReturn(List.of(record));
    CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new IllegalStateException("kafka unavailable"));
    when(eventPublisher.publish(
            eq("orders.submitted.v1"), eq("ord-1002"), any(EventEnvelope.class)))
        .thenReturn(failedFuture);

    service.publishPendingEvents();

    verify(outboxRepository, never()).markPublished(eq(outboxId), any(Instant.class));
    ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
    verify(outboxRepository).markFailed(eq(outboxId), errorCaptor.capture());
    assertTrue(errorCaptor.getValue().contains("kafka unavailable"));
  }
}
