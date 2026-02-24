package com.tradingplatform.infra.kafka.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        String correlationId,
        String causationId,
        String tenantId,
        String key,
        T payload
) {
    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId must not be null");
        requireNonBlank(eventType, "eventType");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be >= 1");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        requireNonBlank(producer, "producer");
        requireNonBlank(correlationId, "correlationId");
        requireNonBlank(key, "key");
        Objects.requireNonNull(payload, "payload must not be null");
    }

    public static <T> EventEnvelope<T> of(
            String eventType,
            int eventVersion,
            String producer,
            String correlationId,
            String key,
            T payload
    ) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                eventVersion,
                Instant.now(),
                producer,
                correlationId,
                null,
                null,
                key,
                payload
        );
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
