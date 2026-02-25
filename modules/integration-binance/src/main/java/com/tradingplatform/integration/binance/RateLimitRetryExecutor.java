package com.tradingplatform.integration.binance;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;

public class RateLimitRetryExecutor {
  private static final String RETRY_COUNTER = "connector.binance.rate_limit.retry";
  private static final String EXHAUSTED_COUNTER = "connector.binance.rate_limit.exhausted";

  private final int maxAttempts;
  private final RetryAfterParser retryAfterParser;
  private final JitteredExponentialBackoff backoff;
  private final Sleeper sleeper;
  private final MeterRegistry meterRegistry;

  public RateLimitRetryExecutor(
      int maxAttempts,
      RetryAfterParser retryAfterParser,
      JitteredExponentialBackoff backoff,
      MeterRegistry meterRegistry) {
    this(maxAttempts, retryAfterParser, backoff, Thread::sleep, meterRegistry);
  }

  public RateLimitRetryExecutor(
      int maxAttempts,
      RetryAfterParser retryAfterParser,
      JitteredExponentialBackoff backoff,
      Sleeper sleeper,
      MeterRegistry meterRegistry) {
    this.maxAttempts = Math.max(1, maxAttempts);
    this.retryAfterParser = Objects.requireNonNull(retryAfterParser, "retryAfterParser must not be null");
    this.backoff = Objects.requireNonNull(backoff, "backoff must not be null");
    this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
  }

  public <T> T execute(Operation<T> operation) {
    int attempt = 1;
    while (true) {
      try {
        return operation.run();
      } catch (BinanceApiException ex) {
        if (!ex.isRateLimitError() || attempt >= maxAttempts) {
          if (ex.isRateLimitError()) {
            meterRegistry.counter(EXHAUSTED_COUNTER).increment();
          }
          throw ex;
        }
        Duration wait = resolveBackoff(ex, attempt);
        meterRegistry.counter(RETRY_COUNTER).increment();
        sleep(wait);
        attempt++;
      }
    }
  }

  private Duration resolveBackoff(BinanceApiException ex, int attempt) {
    Duration computed = backoff.backoffForAttempt(attempt);
    return ex.retryAfterHeader()
        .flatMap(retryAfterParser::parse)
        .map(retryAfter -> minDuration(retryAfter, backoff.maxBackoff()))
        .orElse(computed);
  }

  private void sleep(Duration duration) {
    if (duration.isZero() || duration.isNegative()) {
      return;
    }
    try {
      sleeper.sleep(duration);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted during Binance rate-limit backoff", interrupted);
    }
  }

  private static Duration minDuration(Duration left, Duration right) {
    return left.compareTo(right) <= 0 ? left : right;
  }

  @FunctionalInterface
  public interface Operation<T> {
    T run();
  }

  @FunctionalInterface
  public interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
  }
}
