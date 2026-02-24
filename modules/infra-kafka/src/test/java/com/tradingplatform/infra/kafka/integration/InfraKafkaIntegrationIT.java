package com.tradingplatform.infra.kafka.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tradingplatform.infra.kafka.consumer.EventConsumerAdapter;
import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV1;
import com.tradingplatform.infra.kafka.errors.DeadLetterPublisher;
import com.tradingplatform.infra.kafka.errors.RetryPolicy;
import com.tradingplatform.infra.kafka.observability.KafkaTelemetry;
import com.tradingplatform.infra.kafka.producer.EventPublisher;
import com.tradingplatform.infra.kafka.serde.EventEnvelopeJsonCodec;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import com.tradingplatform.testsupport.containers.KafkaContainerBaseIT;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

@SpringBootTest(
    classes = InfraKafkaIntegrationIT.TestApplication.class,
    properties = {
      "infra.kafka.bootstrap-servers=${spring.kafka.bootstrap-servers}",
      "infra.kafka.consumer.group-id=infra-kafka-it",
      "infra.kafka.consumer.auto-offset-reset=earliest"
    })
class InfraKafkaIntegrationIT extends KafkaContainerBaseIT {
  @Autowired private EventPublisher eventPublisher;

  @Autowired private ReceivedOrderSubmittedEvents receivedEvents;

  @BeforeAll
  static void createTopic() throws Exception {
    try (AdminClient admin =
        AdminClient.create(java.util.Map.of("bootstrap.servers", bootstrapServers()))) {
      admin.createTopics(
              java.util.List.of(new NewTopic(TopicNames.ORDERS_SUBMITTED_V1, 1, (short) 1)))
          .all()
          .get(10, TimeUnit.SECONDS);
    }
  }

  @Test
  void shouldPublishAndConsumeOrderSubmittedEvent() throws Exception {
    OrderSubmittedV1 payload =
        new OrderSubmittedV1(
            "ord-it-1",
            "acc-it-1",
            "BTCUSDT",
            "BUY",
            "LIMIT",
            new BigDecimal("0.01"),
            new BigDecimal("40100.00"),
            Instant.parse("2026-02-24T12:00:00Z"));
    EventEnvelope<OrderSubmittedV1> envelope =
        EventEnvelope.of(
            EventTypes.ORDER_SUBMITTED,
            1,
            "integration-test",
            payload.orderId(),
            payload.orderId(),
            payload);

    eventPublisher
        .publish(TopicNames.ORDERS_SUBMITTED_V1, payload.orderId(), envelope)
        .get(10, TimeUnit.SECONDS);

    EventEnvelope<OrderSubmittedV1> consumed = receivedEvents.await(Duration.ofSeconds(10));
    assertNotNull(consumed);
    assertEquals(EventTypes.ORDER_SUBMITTED, consumed.eventType());
    assertEquals(1, consumed.eventVersion());
    assertEquals(payload.orderId(), consumed.payload().orderId());
    assertEquals(payload.accountId(), consumed.payload().accountId());
  }

  @SpringBootApplication
  @EnableKafka
  static class TestApplication {
    @Bean
    ReceivedOrderSubmittedEvents receivedOrderSubmittedEvents() {
      return new ReceivedOrderSubmittedEvents();
    }

    @Bean
    EventConsumerAdapter<OrderSubmittedV1> orderSubmittedAdapter(
        EventEnvelopeJsonCodec codec,
        DeadLetterPublisher deadLetterPublisher,
        RetryPolicy retryPolicy,
        KafkaTelemetry telemetry,
        ReceivedOrderSubmittedEvents sink) {
      return new EventConsumerAdapter<>(
          OrderSubmittedV1.class,
          EventTypes.ORDER_SUBMITTED,
          1,
          codec,
          sink::accept,
          deadLetterPublisher,
          retryPolicy,
          telemetry);
    }

    @Bean
    TestOrderSubmittedListener testOrderSubmittedListener(
        EventConsumerAdapter<OrderSubmittedV1> adapter) {
      return new TestOrderSubmittedListener(adapter);
    }
  }

  static class TestOrderSubmittedListener {
    private final EventConsumerAdapter<OrderSubmittedV1> adapter;

    TestOrderSubmittedListener(EventConsumerAdapter<OrderSubmittedV1> adapter) {
      this.adapter = adapter;
    }

    @KafkaListener(
        topics = TopicNames.ORDERS_SUBMITTED_V1,
        groupId = "infra-kafka-it-listener",
        containerFactory = "infraKafkaListenerContainerFactory")
    void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
      adapter.process(record);
      ack.acknowledge();
    }
  }

  static class ReceivedOrderSubmittedEvents {
    private final BlockingQueue<EventEnvelope<OrderSubmittedV1>> queue =
        new LinkedBlockingQueue<>();

    void accept(EventEnvelope<OrderSubmittedV1> envelope) {
      queue.offer(envelope);
    }

    EventEnvelope<OrderSubmittedV1> await(Duration timeout) throws InterruptedException {
      return queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
  }
}
