package com.tradingplatform.integration.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JitteredExponentialBackoffTest {
  @Test
  void shouldComputeDeterministicBackoffWhenJitterDisabled() {
    JitteredExponentialBackoff backoff = new JitteredExponentialBackoff(100L, 500L, false);

    assertEquals(Duration.ofMillis(100L), backoff.backoffForAttempt(1));
    assertEquals(Duration.ofMillis(200L), backoff.backoffForAttempt(2));
    assertEquals(Duration.ofMillis(400L), backoff.backoffForAttempt(3));
    assertEquals(Duration.ofMillis(500L), backoff.backoffForAttempt(4));
  }

  @Test
  void shouldApplyJitterWhenEnabled() {
    JitteredExponentialBackoff backoff =
        new JitteredExponentialBackoff(200L, 500L, true, () -> 0.5d);

    Duration actual = backoff.backoffForAttempt(2);

    assertEquals(Duration.ofMillis(200L), actual);
  }
}
