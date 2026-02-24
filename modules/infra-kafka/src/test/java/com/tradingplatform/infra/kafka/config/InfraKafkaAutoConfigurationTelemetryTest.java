package com.tradingplatform.infra.kafka.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tradingplatform.infra.kafka.observability.KafkaTelemetry;
import com.tradingplatform.infra.kafka.observability.MicrometerKafkaTelemetry;
import com.tradingplatform.infra.kafka.observability.NoOpKafkaTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InfraKafkaAutoConfigurationTelemetryTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(InfraKafkaAutoConfiguration.class);

  @Test
  void shouldUseNoOpKafkaTelemetryWhenMeterRegistryIsMissing() {
    contextRunner.run(
        context -> assertEquals(NoOpKafkaTelemetry.class, context.getBean(KafkaTelemetry.class).getClass()));
  }

  @Test
  void shouldUseMicrometerKafkaTelemetryWhenMeterRegistryIsPresent() {
    contextRunner
        .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context ->
                assertEquals(
                    MicrometerKafkaTelemetry.class, context.getBean(KafkaTelemetry.class).getClass()));
  }
}
