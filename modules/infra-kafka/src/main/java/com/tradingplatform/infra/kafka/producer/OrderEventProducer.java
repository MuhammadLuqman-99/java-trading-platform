package com.tradingplatform.infra.kafka.producer;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV1;
import com.tradingplatform.infra.kafka.contract.payload.OrderUpdatedV1;
import com.tradingplatform.infra.kafka.contract.payload.OrderUpdatedV3;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.support.SendResult;

public class OrderEventProducer {
  private static final int EVENT_VERSION_V1 = 1;
  private static final int EVENT_VERSION_V3 = 3;

  private final EventPublisher eventPublisher;
  private final String producerName;

  public OrderEventProducer(EventPublisher eventPublisher, String producerName) {
    this.eventPublisher = eventPublisher;
    this.producerName = producerName;
  }

  public CompletableFuture<SendResult<String, String>> publishOrderSubmitted(OrderSubmittedV1 payload) {
    String key = requireKey(payload.orderId(), "payload.orderId");
    EventEnvelope<OrderSubmittedV1> envelope =
        EventEnvelope.of(
            EventTypes.ORDER_SUBMITTED, EVENT_VERSION_V1, producerName, key, key, payload);
    return eventPublisher.publish(TopicNames.ORDERS_SUBMITTED_V1, key, envelope);
  }

  public CompletableFuture<SendResult<String, String>> publishOrderUpdated(OrderUpdatedV1 payload) {
    String key = requireKey(payload.orderId(), "payload.orderId");
    EventEnvelope<OrderUpdatedV1> envelope =
        EventEnvelope.of(EventTypes.ORDER_UPDATED, EVENT_VERSION_V1, producerName, key, key, payload);
    return eventPublisher.publish(TopicNames.ORDERS_UPDATED_V1, key, envelope);
  }

  public CompletableFuture<SendResult<String, String>> publishOrderUpdatedV3(OrderUpdatedV3 payload) {
    String key = requireKey(payload.orderId(), "payload.orderId");
    EventEnvelope<OrderUpdatedV3> envelope =
        EventEnvelope.of(EventTypes.ORDER_UPDATED, EVENT_VERSION_V3, producerName, key, key, payload);
    return eventPublisher.publish(TopicNames.ORDERS_UPDATED_V3, key, envelope);
  }

  private static String requireKey(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
