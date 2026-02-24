package com.tradingplatform.infra.kafka.topics;

import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;

public final class KafkaTopicDefinitions {
  private KafkaTopicDefinitions() {}

  public static List<KafkaTopicDefinition> defaults(int partitions, short replicationFactor) {
    return TopicNames.all().stream()
        .map(name -> new KafkaTopicDefinition(name, partitions, replicationFactor))
        .toList();
  }

  public record KafkaTopicDefinition(String name, int partitions, short replicationFactor) {
    public KafkaTopicDefinition {
      TopicNameValidator.assertValid(name);
      if (partitions < 1) {
        throw new IllegalArgumentException("partitions must be >= 1");
      }
      if (replicationFactor < 1) {
        throw new IllegalArgumentException("replicationFactor must be >= 1");
      }
    }

    public NewTopic toNewTopic() {
      return new NewTopic(name, partitions, replicationFactor);
    }
  }
}
