package com.tradingplatform.infra.kafka.errors;

import com.tradingplatform.infra.kafka.config.InfraKafkaProperties;
import com.tradingplatform.infra.kafka.topics.TopicNameValidator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaDeadLetterPublisher implements DeadLetterPublisher {
  private static final Logger log = LoggerFactory.getLogger(KafkaDeadLetterPublisher.class);

  private static final String HEADER_SOURCE_TOPIC = "x-dlq-source-topic";
  private static final String HEADER_SOURCE_PARTITION = "x-dlq-source-partition";
  private static final String HEADER_SOURCE_OFFSET = "x-dlq-source-offset";
  private static final String HEADER_EXCEPTION_CLASS = "x-dlq-exception-class";
  private static final String HEADER_EXCEPTION_MESSAGE = "x-dlq-exception-message";
  private static final String HEADER_FAILED_AT = "x-dlq-failed-at";

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final InfraKafkaProperties.DeadLetter properties;

  public KafkaDeadLetterPublisher(
      KafkaTemplate<String, String> kafkaTemplate, InfraKafkaProperties.DeadLetter properties) {
    this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  @Override
  public void publish(
      String sourceTopic, ConsumerRecord<String, String> failedRecord, Exception exception) {
    String targetTopic = resolveDeadLetterTopic(sourceTopic);
    String payload = properties.isIncludePayload() ? failedRecord.value() : null;

    ProducerRecord<String, String> deadLetterRecord =
        new ProducerRecord<>(targetTopic, failedRecord.key(), payload);
    deadLetterRecord
        .headers()
        .add(HEADER_SOURCE_TOPIC, sourceTopic.getBytes(StandardCharsets.UTF_8));
    deadLetterRecord
        .headers()
        .add(
            HEADER_SOURCE_PARTITION,
            Integer.toString(failedRecord.partition()).getBytes(StandardCharsets.UTF_8));
    deadLetterRecord
        .headers()
        .add(
            HEADER_SOURCE_OFFSET,
            Long.toString(failedRecord.offset()).getBytes(StandardCharsets.UTF_8));
    deadLetterRecord
        .headers()
        .add(
            HEADER_EXCEPTION_CLASS,
            exception.getClass().getName().getBytes(StandardCharsets.UTF_8));
    deadLetterRecord
        .headers()
        .add(
            HEADER_EXCEPTION_MESSAGE,
            safeMessage(exception.getMessage()).getBytes(StandardCharsets.UTF_8));
    deadLetterRecord
        .headers()
        .add(HEADER_FAILED_AT, Instant.now().toString().getBytes(StandardCharsets.UTF_8));

    kafkaTemplate
        .send(deadLetterRecord)
        .whenComplete(
            (result, throwable) -> {
              if (throwable != null) {
                log.error(
                    "Failed to publish DLQ record sourceTopic={} targetTopic={} partition={} offset={}",
                    sourceTopic,
                    targetTopic,
                    failedRecord.partition(),
                    failedRecord.offset(),
                    throwable);
                return;
              }
              log.warn(
                  "Published DLQ record sourceTopic={} targetTopic={} partition={} offset={}",
                  sourceTopic,
                  targetTopic,
                  failedRecord.partition(),
                  failedRecord.offset());
            });
  }

  private String resolveDeadLetterTopic(String sourceTopic) {
    String suffix = normalizeSuffix(properties.getTopicSuffix());
    if (sourceTopic.endsWith(".v1")) {
      String base = sourceTopic.substring(0, sourceTopic.length() - 3);
      String topic = base + suffix;
      TopicNameValidator.assertValid(topic);
      return topic;
    }
    String topic = sourceTopic + suffix;
    TopicNameValidator.assertValid(topic);
    return topic;
  }

  private static String normalizeSuffix(String configuredSuffix) {
    if (configuredSuffix == null || configuredSuffix.isBlank()) {
      return ".dlq.v1";
    }
    return configuredSuffix.startsWith(".") ? configuredSuffix : "." + configuredSuffix;
  }

  private static String safeMessage(String message) {
    if (message == null || message.isBlank()) {
      return "no-message";
    }
    return message;
  }
}
