package com.tradingplatform.infra.kafka.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaAdmin;

class InfraKafkaAutoConfigurationTopicsTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(InfraKafkaAutoConfiguration.class);

  @Test
  void shouldRegisterExpectedTopicsWhenEnabled() {
    contextRunner
        .withPropertyValues(
            "infra.kafka.topics.enabled=true",
            "infra.kafka.topics.partitions=3",
            "infra.kafka.topics.replication-factor=1")
        .run(
            context -> {
              assertEquals(1, context.getBeansOfType(KafkaAdmin.NewTopics.class).size());
              context.getBean("infraKafkaTopics");
            });
  }

  @Test
  void shouldRegisterNoTopicsWhenDisabled() {
    contextRunner
        .withPropertyValues("infra.kafka.topics.enabled=false")
        .run(
            context -> {
              assertEquals(0, context.getBeansOfType(KafkaAdmin.NewTopics.class).size());
            });
  }
}
