package com.tradingplatform.infra.kafka.errors;

import java.time.Duration;

public interface RetryPolicy {
  boolean shouldRetry(int attempt, Exception exception);

  Duration backoffForAttempt(int attempt);

  default boolean isRetryable(Exception exception) {
    return true;
  }
}
