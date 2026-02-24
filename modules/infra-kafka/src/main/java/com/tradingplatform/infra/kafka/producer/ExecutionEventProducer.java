package com.tradingplatform.infra.kafka.producer;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.ExecutionRecordedV1;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.support.SendResult;

public class ExecutionEventProducer {
  private static final int EVENT_VERSION = 1;

  private final EventPublisher eventPublisher;
  private final String producerName;

  public ExecutionEventProducer(EventPublisher eventPublisher, String producerName) {
    this.eventPublisher = eventPublisher;
    this.producerName = producerName;
  }

  public CompletableFuture<SendResult<String, String>> publishExecutionRecorded(
      ExecutionRecordedV1 payload) {
    String key = requireKey(payload.orderId(), "payload.orderId");
    EventEnvelope<ExecutionRecordedV1> envelope =
        EventEnvelope.of(
            EventTypes.EXECUTION_RECORDED, EVENT_VERSION, producerName, key, key, payload);
    return eventPublisher.publish(TopicNames.EXECUTIONS_RECORDED_V1, key, envelope);
  }

  private static String requireKey(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
