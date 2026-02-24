package com.tradingplatform.infra.kafka.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InfraKafkaPropertiesTest {
  @Test
  void shouldUseNestedValuesByDefault() {
    InfraKafkaProperties properties = new InfraKafkaProperties();

    assertEquals("trading-platform-producer", properties.effectiveProducerClientId());
    assertEquals("cg-default", properties.effectiveConsumerGroupId());
    assertEquals("earliest", properties.effectiveAutoOffsetReset());
    assertTrue(properties.effectiveProducerIdempotenceEnabled());
    assertTrue(properties.getTopics().isEnabled());
    assertEquals(3, properties.getTopics().getPartitions());
    assertEquals(1, properties.getTopics().getReplicationFactor());
    assertTrue(properties.getDeadLetter().isEnabled());
    assertEquals("topic", properties.getDeadLetter().getMode());
    assertEquals(".dlq.v1", properties.getDeadLetter().getTopicSuffix());
    assertTrue(properties.getDeadLetter().isIncludePayload());
  }

  @Test
  void shouldPreferLegacyValuesWhenProvided() {
    InfraKafkaProperties properties = new InfraKafkaProperties();
    properties.setProducerClientId("legacy-client");
    properties.setConsumerGroupId("legacy-group");
    properties.setAutoOffsetReset("latest");
    properties.setProducerRetries(7);
    properties.setProducerIdempotenceEnabled(false);

    assertEquals("legacy-client", properties.effectiveProducerClientId());
    assertEquals("legacy-group", properties.effectiveConsumerGroupId());
    assertEquals("latest", properties.effectiveAutoOffsetReset());
    assertEquals(7, properties.effectiveProducerRetries());
    assertFalse(properties.effectiveProducerIdempotenceEnabled());
  }
}
