package com.tradingplatform.infra.kafka.errors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExponentialBackoffRetryPolicyTest {
  @Test
  void shouldIncreaseBackoffWithCap() {
    ExponentialBackoffRetryPolicy policy =
        new ExponentialBackoffRetryPolicy(
            4, Duration.ofMillis(100), Duration.ofMillis(500), 2.0d);

    assertEquals(Duration.ofMillis(100), policy.backoffForAttempt(1));
    assertEquals(Duration.ofMillis(200), policy.backoffForAttempt(2));
    assertEquals(Duration.ofMillis(400), policy.backoffForAttempt(3));
    assertEquals(Duration.ofMillis(500), policy.backoffForAttempt(4));
  }

  @Test
  void shouldRetryUntilMaxAttempts() {
    ExponentialBackoffRetryPolicy policy =
        new ExponentialBackoffRetryPolicy(
            3, Duration.ofMillis(50), Duration.ofMillis(500), 2.0d);

    assertTrue(policy.shouldRetry(1, new IllegalStateException("transient")));
    assertTrue(policy.shouldRetry(2, new IllegalStateException("transient")));
    assertFalse(policy.shouldRetry(3, new IllegalStateException("transient")));
  }
}
