package com.tradingplatform.infra.kafka.errors;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public interface DeadLetterPublisher {
  void publish(
      String sourceTopic, ConsumerRecord<String, String> failedRecord, Exception exception);
}
