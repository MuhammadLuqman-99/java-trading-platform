package com.tradingplatform.infra.kafka.errors;

public class InvalidEventMetadataException extends RuntimeException {
    public InvalidEventMetadataException(String message) {
        super(message);
    }
}
