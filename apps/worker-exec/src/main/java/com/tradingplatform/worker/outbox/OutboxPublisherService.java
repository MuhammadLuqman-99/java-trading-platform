package com.tradingplatform.worker.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.producer.EventPublisher;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    prefix = "outbox.publisher",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxPublisherService {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);

  private final OutboxRepository outboxRepository;
  private final EventPublisher eventPublisher;
  private final OutboxPublisherProperties properties;
  private final ObjectMapper objectMapper;

  public OutboxPublisherService(
      OutboxRepository outboxRepository,
      EventPublisher eventPublisher,
      OutboxPublisherProperties properties,
      ObjectMapper objectMapper) {
    this.outboxRepository = outboxRepository;
    this.eventPublisher = eventPublisher;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:1000}")
  public void publishPendingEvents() {
    List<OutboxEventRecord> pending = outboxRepository.findPendingBatch(properties.getBatchSize());
    if (pending.isEmpty()) {
      return;
    }

    for (OutboxEventRecord record : pending) {
      publishSingle(record);
    }
  }

  private void publishSingle(OutboxEventRecord record) {
    try {
      JsonNode payload = parsePayload(record.eventPayload());
      EventEnvelope<JsonNode> envelope =
          EventEnvelope.of(
              record.eventType(),
              1,
              properties.getProducerName(),
              correlationIdFor(record),
              messageKeyFor(record),
              payload);

      eventPublisher.publish(record.topic(), messageKeyFor(record), envelope).join();
      outboxRepository.markPublished(record.id(), Instant.now());

      log.info(
          "Outbox publish success outbox_id={} topic={} event_type={} attempt_count={}",
          record.id(),
          record.topic(),
          record.eventType(),
          record.attemptCount());
    } catch (Exception ex) {
      String error = errorMessage(ex);
      outboxRepository.markFailed(record.id(), error);
      log.warn(
          "Outbox publish failed outbox_id={} topic={} event_type={} error={}",
          record.id(),
          record.topic(),
          record.eventType(),
          error);
    }
  }

  private JsonNode parsePayload(String payloadJson) throws IOException {
    if (payloadJson == null || payloadJson.isBlank()) {
      return objectMapper.createObjectNode();
    }
    return objectMapper.readTree(payloadJson);
  }

  private static String correlationIdFor(OutboxEventRecord record) {
    if (record.aggregateId() != null && !record.aggregateId().isBlank()) {
      return record.aggregateId();
    }
    return record.id().toString();
  }

  private static String messageKeyFor(OutboxEventRecord record) {
    if (record.eventKey() != null && !record.eventKey().isBlank()) {
      return record.eventKey();
    }
    if (record.aggregateId() != null && !record.aggregateId().isBlank()) {
      return record.aggregateId();
    }
    return record.id().toString();
  }

  private static String errorMessage(Exception ex) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) {
      return ex.getClass().getSimpleName();
    }
    return message;
  }
}
