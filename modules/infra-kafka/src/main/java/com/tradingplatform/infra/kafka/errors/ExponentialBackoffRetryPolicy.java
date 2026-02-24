package com.tradingplatform.infra.kafka.errors;

import java.time.Duration;
import java.util.Objects;

public class ExponentialBackoffRetryPolicy implements RetryPolicy {
  private final int maxAttempts;
  private final Duration initialBackoff;
  private final Duration maxBackoff;
  private final double multiplier;

  public ExponentialBackoffRetryPolicy(
      int maxAttempts, Duration initialBackoff, Duration maxBackoff, double multiplier) {
    this.maxAttempts = Math.max(1, maxAttempts);
    this.initialBackoff = Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
    this.maxBackoff = Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");
    this.multiplier = Math.max(1.0d, multiplier);
  }

  @Override
  public boolean shouldRetry(int attempt, Exception exception) {
    return attempt < maxAttempts;
  }

  @Override
  public Duration backoffForAttempt(int attempt) {
    long initialMillis = Math.max(0L, initialBackoff.toMillis());
    long maxMillis = Math.max(initialMillis, maxBackoff.toMillis());
    if (initialMillis == 0L) {
      return Duration.ZERO;
    }

    int exponent = Math.max(0, attempt - 1);
    double scaled = initialMillis * Math.pow(multiplier, exponent);
    long bounded = (long) Math.min(maxMillis, scaled);
    return Duration.ofMillis(Math.max(0L, bounded));
  }
}
