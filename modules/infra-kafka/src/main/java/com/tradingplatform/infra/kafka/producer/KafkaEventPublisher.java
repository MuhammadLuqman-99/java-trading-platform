package com.tradingplatform.infra.kafka.producer;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.contract.EventHeaders;
import com.tradingplatform.infra.kafka.observability.KafkaTelemetry;
import com.tradingplatform.infra.kafka.serde.EventEnvelopeJsonCodec;
import com.tradingplatform.infra.kafka.topics.TopicNameValidator;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class KafkaEventPublisher implements EventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventEnvelopeJsonCodec codec;
    private final KafkaTelemetry telemetry;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            EventEnvelopeJsonCodec codec,
            KafkaTelemetry telemetry
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.codec = codec;
        this.telemetry = telemetry;
    }

    @Override
    public <T> CompletableFuture<SendResult<String, String>> publish(String topic, String key, EventEnvelope<T> envelope) {
        TopicNameValidator.assertValid(topic);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Kafka key must not be blank");
        }

        long started = System.nanoTime();
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, codec.encode(envelope));
        addHeaders(record, envelope);

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
        return future.whenComplete((result, ex) -> {
            if (ex == null) {
                telemetry.onPublishSuccess(topic, key, envelope.eventType(), System.nanoTime() - started);
            } else {
                telemetry.onPublishFailure(topic, key, envelope.eventType(), ex);
            }
        });
    }

    private static void addHeaders(ProducerRecord<String, String> record, EventEnvelope<?> envelope) {
        record.headers().add(EventHeaders.X_EVENT_TYPE, envelope.eventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add(
                EventHeaders.X_EVENT_VERSION,
                Integer.toString(envelope.eventVersion()).getBytes(StandardCharsets.UTF_8)
        );
        record.headers().add(
                EventHeaders.X_CORRELATION_ID,
                envelope.correlationId().getBytes(StandardCharsets.UTF_8)
        );
        record.headers().add(EventHeaders.CONTENT_TYPE, EventHeaders.APPLICATION_JSON.getBytes(StandardCharsets.UTF_8));
    }
}
