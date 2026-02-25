package com.tradingplatform.integration.binance;

import java.time.Duration;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.concurrent.ThreadLocalRandom;

public class JitteredExponentialBackoff {
  private final long baseBackoffMs;
  private final long maxBackoffMs;
  private final boolean jitterEnabled;
  private final DoubleSupplier jitterSource;

  public JitteredExponentialBackoff(long baseBackoffMs, long maxBackoffMs, boolean jitterEnabled) {
    this(baseBackoffMs, maxBackoffMs, jitterEnabled, ThreadLocalRandom.current()::nextDouble);
  }

  public JitteredExponentialBackoff(
      long baseBackoffMs, long maxBackoffMs, boolean jitterEnabled, DoubleSupplier jitterSource) {
    this.baseBackoffMs = Math.max(0L, baseBackoffMs);
    this.maxBackoffMs = Math.max(this.baseBackoffMs, maxBackoffMs);
    this.jitterEnabled = jitterEnabled;
    this.jitterSource = Objects.requireNonNull(jitterSource, "jitterSource must not be null");
  }

  public Duration backoffForAttempt(int attempt) {
    long deterministic = deterministicBackoff(attempt);
    if (!jitterEnabled || deterministic == 0L) {
      return Duration.ofMillis(deterministic);
    }
    double factor = Math.max(0.0d, Math.min(0.999999999d, jitterSource.getAsDouble()));
    long jittered = (long) Math.floor(factor * (deterministic + 1L));
    return Duration.ofMillis(Math.max(0L, Math.min(maxBackoffMs, jittered)));
  }

  public Duration maxBackoff() {
    return Duration.ofMillis(maxBackoffMs);
  }

  private long deterministicBackoff(int attempt) {
    if (baseBackoffMs == 0L) {
      return 0L;
    }
    int exponent = Math.max(0, attempt - 1);
    double scaled = baseBackoffMs * Math.pow(2.0d, exponent);
    long bounded = (long) Math.floor(Math.min((double) maxBackoffMs, scaled));
    return Math.max(0L, bounded);
  }
}
