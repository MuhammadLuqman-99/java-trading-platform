package com.tradingplatform.infra.kafka.producer;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

public interface EventPublisher {
    <T> CompletableFuture<SendResult<String, String>> publish(String topic, String key, EventEnvelope<T> envelope);
}
