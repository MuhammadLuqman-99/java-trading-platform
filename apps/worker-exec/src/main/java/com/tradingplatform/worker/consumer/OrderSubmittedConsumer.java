package com.tradingplatform.worker.consumer;

import com.tradingplatform.infra.kafka.consumer.EventConsumerAdapter;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV1;
import com.tradingplatform.infra.kafka.errors.DeadLetterPublisher;
import com.tradingplatform.infra.kafka.errors.RetryPolicy;
import com.tradingplatform.infra.kafka.observability.KafkaTelemetry;
import com.tradingplatform.infra.kafka.serde.EventEnvelopeJsonCodec;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import com.tradingplatform.worker.execution.ExecutionOrderAdapter;
import com.tradingplatform.worker.execution.SubmitOrderCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OrderSubmittedConsumer {
  private final EventConsumerAdapter<OrderSubmittedV1> adapter;
  private final ExecutionOrderAdapter executionOrderAdapter;

  public OrderSubmittedConsumer(
      EventEnvelopeJsonCodec codec,
      DeadLetterPublisher deadLetterPublisher,
      RetryPolicy retryPolicy,
      KafkaTelemetry telemetry,
      ExecutionOrderAdapter executionOrderAdapter) {
    this.executionOrderAdapter = executionOrderAdapter;
    this.adapter =
        new EventConsumerAdapter<>(
            OrderSubmittedV1.class,
            EventTypes.ORDER_SUBMITTED,
            1,
            codec,
            this::handleEvent,
            deadLetterPublisher,
            retryPolicy,
            telemetry);
  }

  @KafkaListener(
      topics = TopicNames.ORDERS_SUBMITTED_V1,
      groupId = "${infra.kafka.consumer.group-id:${infra.kafka.consumer-group-id:cg-exec-adapter}}",
      containerFactory = "infraKafkaListenerContainerFactory")
  public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
    adapter.process(record, 1);
    ack.acknowledge();
  }

  private void handleEvent(
      com.tradingplatform.infra.kafka.contract.EventEnvelope<OrderSubmittedV1> envelope) {
    OrderSubmittedV1 payload = envelope.payload();
    SubmitOrderCommand command =
        new SubmitOrderCommand(
            payload.orderId(),
            payload.accountId(),
            payload.instrument(),
            payload.side(),
            payload.type(),
            payload.qty(),
            payload.price(),
            payload.submittedAt(),
            envelope.correlationId(),
            envelope.eventId());
    executionOrderAdapter.submitOrder(command);
  }
}
