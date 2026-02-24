package com.tradingplatform.infra.kafka.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tradingplatform.infra.kafka.errors.ExponentialBackoffRetryPolicy;
import com.tradingplatform.infra.kafka.errors.RetryPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryPolicyFactoryTest {
  @Test
  void shouldCreateFixedRetryPolicyByDefault() {
    InfraKafkaProperties.Retry retry = new InfraKafkaProperties.Retry();
    retry.setMode("fixed");
    retry.setMaxAttempts(3);
    retry.setFixedBackoffMs(200);

    RetryPolicy policy = RetryPolicyFactory.create(retry);
    assertTrue(policy.shouldRetry(1, new IllegalStateException("boom")));
    assertEquals(200L, policy.backoffForAttempt(1).toMillis());
  }

  @Test
  void shouldCreateExponentialRetryPolicy() {
    InfraKafkaProperties.Retry retry = new InfraKafkaProperties.Retry();
    retry.setMode("exponential");
    retry.setMaxAttempts(3);
    retry.setInitialBackoffMs(100);
    retry.setMaxBackoffMs(1000);
    retry.setMultiplier(2.0d);

    RetryPolicy policy = RetryPolicyFactory.create(retry);
    assertTrue(policy instanceof ExponentialBackoffRetryPolicy);
    assertEquals(100L, policy.backoffForAttempt(1).toMillis());
    assertEquals(200L, policy.backoffForAttempt(2).toMillis());
  }

  @Test
  void shouldApplyRetryableExceptionAllowListWhenConfigured() {
    InfraKafkaProperties.Retry retry = new InfraKafkaProperties.Retry();
    retry.setMode("fixed");
    retry.setMaxAttempts(3);
    retry.setFixedBackoffMs(0);
    retry.setRetryableExceptions(List.of(IllegalStateException.class.getName()));

    RetryPolicy policy = RetryPolicyFactory.create(retry);
    assertTrue(policy.isRetryable(new IllegalStateException("ok")));
    assertFalse(policy.isRetryable(new RuntimeException("blocked")));
  }

  @Test
  void shouldFailOnUnsupportedRetryMode() {
    InfraKafkaProperties.Retry retry = new InfraKafkaProperties.Retry();
    retry.setMode("unknown");

    assertThrows(IllegalArgumentException.class, () -> RetryPolicyFactory.create(retry));
  }
}
