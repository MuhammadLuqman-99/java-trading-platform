package com.tradingplatform.infra.kafka.topics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class KafkaTopicDefinitionsTest {
  @Test
  void shouldCreateDefinitionsForAllKnownTopics() {
    var definitions = KafkaTopicDefinitions.defaults(3, (short) 1);
    assertEquals(TopicNames.all().size(), definitions.size());

    Set<String> names =
        definitions.stream()
            .map(KafkaTopicDefinitions.KafkaTopicDefinition::name)
            .collect(Collectors.toSet());
    assertEquals(Set.copyOf(TopicNames.all()), names);
    assertEquals(3, definitions.get(0).toNewTopic().numPartitions());
    assertEquals(1, definitions.get(0).toNewTopic().replicationFactor());
  }

  @Test
  void shouldRejectInvalidProvisioningValues() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new KafkaTopicDefinitions.KafkaTopicDefinition(TopicNames.ORDERS_SUBMITTED_V1, 0, (short) 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new KafkaTopicDefinitions.KafkaTopicDefinition(TopicNames.ORDERS_SUBMITTED_V1, 3, (short) 0));
  }
}
