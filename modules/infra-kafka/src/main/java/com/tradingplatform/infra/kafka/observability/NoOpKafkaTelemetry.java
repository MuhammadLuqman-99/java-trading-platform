package com.tradingplatform.infra.kafka.observability;

public class NoOpKafkaTelemetry implements KafkaTelemetry {
    @Override
    public void onPublishSuccess(String topic, String key, String eventType, long durationNanos) {
    }

    @Override
    public void onPublishFailure(String topic, String key, String eventType, Throwable error) {
    }

    @Override
    public void onConsumeSuccess(String topic, String key, String eventType, int partition, long offset, long durationNanos) {
    }

    @Override
    public void onConsumeFailure(String topic, String key, String eventType, Throwable error) {
    }

    @Override
    public void onDeadLetter(String topic, String key, Throwable error) {
    }
}
