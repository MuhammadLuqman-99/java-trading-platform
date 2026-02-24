package com.tradingplatform.infra.kafka.errors;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class ExceptionFilteringRetryPolicy implements RetryPolicy {
  private final RetryPolicy delegate;
  private final List<Class<? extends Throwable>> retryableExceptions;

  public ExceptionFilteringRetryPolicy(
      RetryPolicy delegate, List<Class<? extends Throwable>> retryableExceptions) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    this.retryableExceptions =
        List.copyOf(Objects.requireNonNull(retryableExceptions, "retryableExceptions must not be null"));
  }

  @Override
  public boolean shouldRetry(int attempt, Exception exception) {
    return isRetryable(exception) && delegate.shouldRetry(attempt, exception);
  }

  @Override
  public Duration backoffForAttempt(int attempt) {
    return delegate.backoffForAttempt(attempt);
  }

  @Override
  public boolean isRetryable(Exception exception) {
    if (retryableExceptions.isEmpty()) {
      return delegate.isRetryable(exception);
    }

    Throwable candidate = exception;
    while (candidate != null) {
      for (Class<? extends Throwable> retryableType : retryableExceptions) {
        if (retryableType.isAssignableFrom(candidate.getClass())) {
          return true;
        }
      }
      candidate = candidate.getCause();
    }
    return false;
  }
}
