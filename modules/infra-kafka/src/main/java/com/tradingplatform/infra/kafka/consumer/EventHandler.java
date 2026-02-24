package com.tradingplatform.infra.kafka.consumer;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;

@FunctionalInterface
public interface EventHandler<T> {
  void handle(EventEnvelope<T> envelope) throws Exception;
}
