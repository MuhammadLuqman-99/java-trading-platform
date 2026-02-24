package com.tradingplatform.infra.kafka.producer;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.support.SendResult;

public interface EventPublisher {
  <T> CompletableFuture<SendResult<String, String>> publish(
      String topic, String key, EventEnvelope<T> envelope);
}
