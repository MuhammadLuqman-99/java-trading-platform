package com.tradingplatform.infra.kafka.serde;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.infra.kafka.contract.EventEnvelope;

public class EventEnvelopeJsonCodec {
    private final ObjectMapper objectMapper;

    public EventEnvelopeJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(EventEnvelope<?> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to encode event envelope", ex);
        }
    }

    public <T> EventEnvelope<T> decode(String json, Class<T> payloadType) {
        try {
            JavaType envelopeType = objectMapper.getTypeFactory()
                    .constructParametricType(EventEnvelope.class, payloadType);
            return objectMapper.readValue(json, envelopeType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to decode event envelope", ex);
        }
    }
}
