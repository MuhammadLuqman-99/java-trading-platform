package com.tradingplatform.infra.kafka.producer;

public class KafkaPublishException extends RuntimeException {
  private final String topic;
  private final String key;
  private final String eventType;

  public KafkaPublishException(String topic, String key, String eventType, String message, Throwable cause) {
    super(message, cause);
    this.topic = topic;
    this.key = key;
    this.eventType = eventType;
  }

  public String getTopic() {
    return topic;
  }

  public String getKey() {
    return key;
  }

  public String getEventType() {
    return eventType;
  }
}
