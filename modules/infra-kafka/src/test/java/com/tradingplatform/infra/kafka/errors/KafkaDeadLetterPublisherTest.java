package com.tradingplatform.infra.kafka.errors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingplatform.infra.kafka.config.InfraKafkaProperties;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaDeadLetterPublisherTest {
  @Test
  void shouldPublishToDerivedDlqTopicWithMetadataHeaders() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));

    InfraKafkaProperties.DeadLetter properties = new InfraKafkaProperties.DeadLetter();
    properties.setEnabled(true);
    properties.setMode("topic");
    properties.setTopicSuffix(".dlq.v1");
    properties.setIncludePayload(true);

    KafkaDeadLetterPublisher publisher = new KafkaDeadLetterPublisher(kafkaTemplate, properties);
    ConsumerRecord<String, String> failedRecord =
        new ConsumerRecord<>("orders.submitted.v1", 2, 12L, "ord-1", "{\"foo\":\"bar\"}");

    publisher.publish("orders.submitted.v1", failedRecord, new IllegalStateException("boom"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<ProducerRecord<String, String>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(captor.capture());
    ProducerRecord<String, String> dlqRecord = captor.getValue();

    assertEquals("orders.submitted.dlq.v1", dlqRecord.topic());
    assertEquals("ord-1", dlqRecord.key());
    assertEquals("{\"foo\":\"bar\"}", dlqRecord.value());
    assertEquals("orders.submitted.v1", headerValue(dlqRecord, "x-dlq-source-topic"));
    assertEquals("2", headerValue(dlqRecord, "x-dlq-source-partition"));
    assertEquals("12", headerValue(dlqRecord, "x-dlq-source-offset"));
    assertEquals(
        IllegalStateException.class.getName(), headerValue(dlqRecord, "x-dlq-exception-class"));
    assertEquals("boom", headerValue(dlqRecord, "x-dlq-exception-message"));
    assertNotNull(headerValue(dlqRecord, "x-dlq-failed-at"));
  }

  @Test
  void shouldDropPayloadWhenConfigured() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));

    InfraKafkaProperties.DeadLetter properties = new InfraKafkaProperties.DeadLetter();
    properties.setIncludePayload(false);

    KafkaDeadLetterPublisher publisher = new KafkaDeadLetterPublisher(kafkaTemplate, properties);
    ConsumerRecord<String, String> failedRecord =
        new ConsumerRecord<>("orders.updated.v1", 0, 1L, "ord-2", "{\"updated\":true}");

    publisher.publish("orders.updated.v1", failedRecord, new RuntimeException("failed"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<ProducerRecord<String, String>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(captor.capture());
    assertEquals(null, captor.getValue().value());
  }

  private static String headerValue(ProducerRecord<String, String> record, String headerName) {
    Header header = record.headers().lastHeader(headerName);
    assertNotNull(header, "Expected header " + headerName + " to exist");
    return new String(header.value(), StandardCharsets.UTF_8);
  }
}
