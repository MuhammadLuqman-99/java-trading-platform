package com.tradingplatform.infra.kafka.consumer;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.contract.EventHeaders;
import com.tradingplatform.infra.kafka.errors.DeadLetterPublisher;
import com.tradingplatform.infra.kafka.errors.InvalidEventMetadataException;
import com.tradingplatform.infra.kafka.errors.RetryPolicy;
import com.tradingplatform.infra.kafka.errors.RetryableKafkaProcessingException;
import com.tradingplatform.infra.kafka.observability.KafkaTelemetry;
import com.tradingplatform.infra.kafka.serde.EventEnvelopeJsonCodec;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class EventConsumerAdapter<T> {
  private final Class<T> payloadType;
  private final String expectedEventType;
  private final int expectedEventVersion;
  private final EventEnvelopeJsonCodec codec;
  private final EventHandler<T> handler;
  private final DeadLetterPublisher deadLetterPublisher;
  private final RetryPolicy retryPolicy;
  private final KafkaTelemetry telemetry;

  public EventConsumerAdapter(
      Class<T> payloadType,
      String expectedEventType,
      int expectedEventVersion,
      EventEnvelopeJsonCodec codec,
      EventHandler<T> handler,
      DeadLetterPublisher deadLetterPublisher,
      RetryPolicy retryPolicy,
      KafkaTelemetry telemetry) {
    this.payloadType = payloadType;
    this.expectedEventType = expectedEventType;
    this.expectedEventVersion = expectedEventVersion;
    this.codec = codec;
    this.handler = handler;
    this.deadLetterPublisher = deadLetterPublisher;
    this.retryPolicy = retryPolicy;
    this.telemetry = telemetry;
  }

  public void process(ConsumerRecord<String, String> record) {
    process(record, 1);
  }

  public void process(ConsumerRecord<String, String> record, int attempt) {
    long started = System.nanoTime();
    String eventTypeFromHeader = headerValue(record.headers(), EventHeaders.X_EVENT_TYPE);
    try {
      validateMetadataHeaders(record.headers());
      EventEnvelope<T> envelope = codec.decode(record.value(), payloadType);
      validateEnvelopeIdentity(envelope);
      handler.handle(envelope);
      telemetry.onConsumeSuccess(
          record.topic(),
          record.key(),
          envelope.eventType(),
          record.partition(),
          record.offset(),
          System.nanoTime() - started);
    } catch (Exception ex) {
      telemetry.onConsumeFailure(record.topic(), record.key(), eventTypeFromHeader, ex);
      if (retryPolicy.shouldRetry(attempt, ex)) {
        throw new RetryableKafkaProcessingException(
            "Retryable event processing failure", ex, retryPolicy.backoffForAttempt(attempt));
      }
      deadLetterPublisher.publish(record.topic(), record, ex);
      telemetry.onDeadLetter(record.topic(), record.key(), ex);
    }
  }

  private void validateMetadataHeaders(Headers headers) {
    String eventType = headerValue(headers, EventHeaders.X_EVENT_TYPE);
    if (eventType == null || eventType.isBlank()) {
      throw new InvalidEventMetadataException(
          "Missing required header: " + EventHeaders.X_EVENT_TYPE);
    }

    String versionRaw = headerValue(headers, EventHeaders.X_EVENT_VERSION);
    if (versionRaw == null || versionRaw.isBlank()) {
      throw new InvalidEventMetadataException(
          "Missing required header: " + EventHeaders.X_EVENT_VERSION);
    }
    try {
      int version = Integer.parseInt(versionRaw);
      if (version < 1) {
        throw new InvalidEventMetadataException("Header x-event-version must be >= 1");
      }
    } catch (NumberFormatException ex) {
      throw new InvalidEventMetadataException("Header x-event-version is not a valid integer");
    }

    String correlationId = headerValue(headers, EventHeaders.X_CORRELATION_ID);
    if (correlationId == null || correlationId.isBlank()) {
      throw new InvalidEventMetadataException(
          "Missing required header: " + EventHeaders.X_CORRELATION_ID);
    }
  }

  private void validateEnvelopeIdentity(EventEnvelope<T> envelope) {
    if (!expectedEventType.equals(envelope.eventType())) {
      throw new InvalidEventMetadataException(
          "Unexpected event type: expected="
              + expectedEventType
              + " actual="
              + envelope.eventType());
    }
    if (expectedEventVersion != envelope.eventVersion()) {
      throw new InvalidEventMetadataException(
          "Unexpected event version: expected="
              + expectedEventVersion
              + " actual="
              + envelope.eventVersion());
    }
  }

  private String headerValue(Headers headers, String name) {
    Header header = headers.lastHeader(name);
    if (header == null || header.value() == null) {
      return null;
    }
    return new String(header.value(), StandardCharsets.UTF_8);
  }
}
