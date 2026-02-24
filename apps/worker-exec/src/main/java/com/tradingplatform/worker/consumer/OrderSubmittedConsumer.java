package com.tradingplatform.worker.consumer;

import com.tradingplatform.infra.kafka.consumer.EventConsumerAdapter;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV1;
import com.tradingplatform.infra.kafka.errors.DeadLetterPublisher;
import com.tradingplatform.infra.kafka.errors.RetryPolicy;
import com.tradingplatform.infra.kafka.observability.KafkaTelemetry;
import com.tradingplatform.infra.kafka.serde.EventEnvelopeJsonCodec;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OrderSubmittedConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderSubmittedConsumer.class);

    private final EventConsumerAdapter<OrderSubmittedV1> adapter;

    public OrderSubmittedConsumer(
            EventEnvelopeJsonCodec codec,
            DeadLetterPublisher deadLetterPublisher,
            RetryPolicy retryPolicy,
            KafkaTelemetry telemetry
    ) {
        this.adapter = new EventConsumerAdapter<>(
                OrderSubmittedV1.class,
                EventTypes.ORDER_SUBMITTED,
                1,
                codec,
                this::handleEvent,
                deadLetterPublisher,
                retryPolicy,
                telemetry
        );
    }

    @KafkaListener(
            topics = TopicNames.ORDERS_SUBMITTED_V1,
            groupId = "${infra.kafka.consumer-group-id:cg-exec-adapter}",
            containerFactory = "infraKafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        adapter.process(record, 1);
        ack.acknowledge();
    }

    private void handleEvent(com.tradingplatform.infra.kafka.contract.EventEnvelope<OrderSubmittedV1> envelope) {
        OrderSubmittedV1 payload = envelope.payload();
        log.info(
                "Received OrderSubmitted event orderId={} accountId={} instrument={} side={} qty={}",
                payload.orderId(),
                payload.accountId(),
                payload.instrument(),
                payload.side(),
                payload.qty()
        );
    }
}
