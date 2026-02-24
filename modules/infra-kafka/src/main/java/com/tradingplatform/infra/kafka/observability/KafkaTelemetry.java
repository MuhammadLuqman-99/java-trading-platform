package com.tradingplatform.infra.kafka.observability;

public interface KafkaTelemetry {
  void onPublishSuccess(String topic, String key, String eventType, long durationNanos);

  void onPublishFailure(String topic, String key, String eventType, Throwable error);

  void onConsumeSuccess(
      String topic, String key, String eventType, int partition, long offset, long durationNanos);

  void onConsumeFailure(String topic, String key, String eventType, Throwable error);

  void onDeadLetter(String topic, String key, Throwable error);
}
