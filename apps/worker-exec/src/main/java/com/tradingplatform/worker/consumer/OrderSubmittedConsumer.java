package com.tradingplatform.worker.consumer;

import com.tradingplatform.infra.kafka.consumer.EventConsumerAdapter;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV2;
import com.tradingplatform.infra.kafka.errors.DeadLetterPublisher;
import com.tradingplatform.infra.kafka.errors.RetryPolicy;
import com.tradingplatform.infra.kafka.observability.KafkaTelemetry;
import com.tradingplatform.infra.kafka.serde.EventEnvelopeJsonCodec;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import com.tradingplatform.worker.execution.SubmitOrderCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OrderSubmittedConsumer {
  private final EventConsumerAdapter<OrderSubmittedV2> adapter;
  private final OrderSubmissionProcessor orderSubmissionProcessor;

  public OrderSubmittedConsumer(
      EventEnvelopeJsonCodec codec,
      DeadLetterPublisher deadLetterPublisher,
      RetryPolicy retryPolicy,
      KafkaTelemetry telemetry,
      OrderSubmissionProcessor orderSubmissionProcessor) {
    this.orderSubmissionProcessor = orderSubmissionProcessor;
    this.adapter =
        new EventConsumerAdapter<>(
            OrderSubmittedV2.class,
            EventTypes.ORDER_SUBMITTED,
            2,
            codec,
            this::handleEvent,
            deadLetterPublisher,
            retryPolicy,
            telemetry);
  }

  @KafkaListener(
      topics = TopicNames.ORDERS_SUBMITTED_V2,
      groupId = "${infra.kafka.consumer.group-id:${infra.kafka.consumer-group-id:cg-exec-adapter}}",
      containerFactory = "infraKafkaListenerContainerFactory")
  public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
    adapter.process(record, 1);
    ack.acknowledge();
  }

  private void handleEvent(
      com.tradingplatform.infra.kafka.contract.EventEnvelope<OrderSubmittedV2> envelope) {
    OrderSubmittedV2 payload = envelope.payload();
    SubmitOrderCommand command =
        new SubmitOrderCommand(
            payload.orderId(),
            payload.accountId(),
            payload.instrument(),
            payload.side(),
            payload.type(),
            payload.qty(),
            payload.price(),
            payload.clientOrderId(),
            payload.submittedAt(),
            envelope.correlationId(),
            envelope.eventId());
    orderSubmissionProcessor.process(command);
  }
}
