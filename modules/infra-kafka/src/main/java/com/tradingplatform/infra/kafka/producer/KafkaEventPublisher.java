package com.tradingplatform.infra.kafka.producer;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.contract.EventHeaders;
import com.tradingplatform.infra.kafka.observability.KafkaTelemetry;
import com.tradingplatform.infra.kafka.serde.EventEnvelopeJsonCodec;
import com.tradingplatform.infra.kafka.topics.TopicNameValidator;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

public class KafkaEventPublisher implements EventPublisher {
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final EventEnvelopeJsonCodec codec;
  private final KafkaTelemetry telemetry;
  private final Duration sendTimeout;

  public KafkaEventPublisher(
      KafkaTemplate<String, String> kafkaTemplate,
      EventEnvelopeJsonCodec codec,
      KafkaTelemetry telemetry) {
    this(kafkaTemplate, codec, telemetry, Duration.ZERO);
  }

  public KafkaEventPublisher(
      KafkaTemplate<String, String> kafkaTemplate,
      EventEnvelopeJsonCodec codec,
      KafkaTelemetry telemetry,
      Duration sendTimeout) {
    this.kafkaTemplate = kafkaTemplate;
    this.codec = codec;
    this.telemetry = telemetry;
    this.sendTimeout = sendTimeout == null ? Duration.ZERO : sendTimeout;
  }

  @Override
  public <T> CompletableFuture<SendResult<String, String>> publish(
      String topic, String key, EventEnvelope<T> envelope) {
    TopicNameValidator.assertValid(topic);
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Kafka key must not be blank");
    }

    long started = System.nanoTime();
    ProducerRecord<String, String> record =
        new ProducerRecord<>(topic, key, codec.encode(envelope));
    addHeaders(record, envelope);

    CompletableFuture<SendResult<String, String>> sendFuture = kafkaTemplate.send(record);
    CompletableFuture<SendResult<String, String>> effectiveFuture = applyTimeout(sendFuture);

    CompletableFuture<SendResult<String, String>> result = new CompletableFuture<>();
    effectiveFuture.whenComplete(
        (sendResult, throwable) -> {
          if (throwable == null) {
            telemetry.onPublishSuccess(
                topic, key, envelope.eventType(), System.nanoTime() - started);
            result.complete(sendResult);
            return;
          }

          KafkaPublishException publishException =
              wrapPublishException(topic, key, envelope.eventType(), throwable);
          telemetry.onPublishFailure(topic, key, envelope.eventType(), publishException);
          result.completeExceptionally(publishException);
        });
    return result;
  }

  private CompletableFuture<SendResult<String, String>> applyTimeout(
      CompletableFuture<SendResult<String, String>> sendFuture) {
    if (sendTimeout.isZero() || sendTimeout.isNegative()) {
      return sendFuture;
    }
    return sendFuture.orTimeout(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  private KafkaPublishException wrapPublishException(
      String topic, String key, String eventType, Throwable throwable) {
    Throwable cause = unwrap(throwable);
    if (cause instanceof KafkaPublishException existing) {
      return existing;
    }

    String message;
    if (cause instanceof TimeoutException) {
      message =
          "Timed out publishing event to Kafka topic="
              + topic
              + " key="
              + key
              + " eventType="
              + eventType;
    } else {
      message =
          "Failed to publish event to Kafka topic="
              + topic
              + " key="
              + key
              + " eventType="
              + eventType;
    }
    return new KafkaPublishException(topic, key, eventType, message, cause);
  }

  private Throwable unwrap(Throwable throwable) {
    if (throwable instanceof CompletionException completionException
        && completionException.getCause() != null) {
      return completionException.getCause();
    }
    return throwable;
  }

  private static void addHeaders(ProducerRecord<String, String> record, EventEnvelope<?> envelope) {
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
    record
        .headers()
        .add(
            EventHeaders.CONTENT_TYPE,
            EventHeaders.APPLICATION_JSON.getBytes(StandardCharsets.UTF_8));
  }
}
