package com.tradingplatform.infra.kafka.errors;

import java.time.Duration;

public class RetryableKafkaProcessingException extends RuntimeException {
    private final Duration suggestedBackoff;

    public RetryableKafkaProcessingException(String message, Throwable cause, Duration suggestedBackoff) {
        super(message, cause);
        this.suggestedBackoff = suggestedBackoff;
    }

    public Duration getSuggestedBackoff() {
        return suggestedBackoff;
    }
}
