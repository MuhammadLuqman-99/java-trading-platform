package com.tradingplatform.infra.kafka.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tradingplatform.infra.kafka.errors.DeadLetterPublisher;
import com.tradingplatform.infra.kafka.errors.KafkaDeadLetterPublisher;
import com.tradingplatform.infra.kafka.errors.LoggingDeadLetterPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InfraKafkaAutoConfigurationDeadLetterTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(InfraKafkaAutoConfiguration.class);

  @Test
  void shouldUseKafkaDeadLetterPublisherByDefault() {
    contextRunner.run(
        context -> {
          DeadLetterPublisher publisher = context.getBean(DeadLetterPublisher.class);
          assertEquals(KafkaDeadLetterPublisher.class, publisher.getClass());
        });
  }

  @Test
  void shouldFallbackToLoggingDeadLetterPublisherWhenDisabled() {
    contextRunner
        .withPropertyValues("infra.kafka.dead-letter.enabled=false")
        .run(
            context -> {
              DeadLetterPublisher publisher = context.getBean(DeadLetterPublisher.class);
              assertEquals(LoggingDeadLetterPublisher.class, publisher.getClass());
            });
  }

  @Test
  void shouldFallbackToLoggingDeadLetterPublisherForUnsupportedMode() {
    contextRunner
        .withPropertyValues("infra.kafka.dead-letter.mode=db")
        .run(
            context -> {
              DeadLetterPublisher publisher = context.getBean(DeadLetterPublisher.class);
              assertEquals(LoggingDeadLetterPublisher.class, publisher.getClass());
            });
  }
}
