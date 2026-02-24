package com.tradingplatform.infra.kafka.config;

import com.tradingplatform.infra.kafka.errors.ExceptionFilteringRetryPolicy;
import com.tradingplatform.infra.kafka.errors.ExponentialBackoffRetryPolicy;
import com.tradingplatform.infra.kafka.errors.FixedBackoffRetryPolicy;
import com.tradingplatform.infra.kafka.errors.RetryPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class RetryPolicyFactory {
  private RetryPolicyFactory() {}

  public static RetryPolicy create(InfraKafkaProperties.Retry retry) {
    if (retry == null) {
      return new FixedBackoffRetryPolicy(1, Duration.ZERO);
    }

    RetryPolicy basePolicy;
    String mode = retry.getMode() == null ? "fixed" : retry.getMode().trim().toLowerCase();
    if ("exponential".equals(mode)) {
      basePolicy =
          new ExponentialBackoffRetryPolicy(
              retry.getMaxAttempts(),
              Duration.ofMillis(Math.max(0L, retry.getInitialBackoffMs())),
              Duration.ofMillis(Math.max(0L, retry.getMaxBackoffMs())),
              retry.getMultiplier());
    } else if ("fixed".equals(mode)) {
      basePolicy =
          new FixedBackoffRetryPolicy(
              retry.getMaxAttempts(), Duration.ofMillis(Math.max(0L, retry.getFixedBackoffMs())));
    } else {
      throw new IllegalArgumentException("Unsupported infra.kafka.retry.mode: " + retry.getMode());
    }

    List<Class<? extends Throwable>> retryableTypes =
        resolveRetryableExceptionTypes(retry.getRetryableExceptions());
    if (retryableTypes.isEmpty()) {
      return basePolicy;
    }
    return new ExceptionFilteringRetryPolicy(basePolicy, retryableTypes);
  }

  private static List<Class<? extends Throwable>> resolveRetryableExceptionTypes(
      List<String> configuredTypes) {
    List<Class<? extends Throwable>> resolvedTypes = new ArrayList<>();
    if (configuredTypes == null) {
      return resolvedTypes;
    }

    for (String configuredType : configuredTypes) {
      if (configuredType == null || configuredType.isBlank()) {
        continue;
      }
      try {
        Class<?> clazz = Class.forName(configuredType.trim());
        if (!Throwable.class.isAssignableFrom(clazz)) {
          throw new IllegalArgumentException(
              "Retryable exception type is not a Throwable: " + configuredType);
        }
        @SuppressWarnings("unchecked")
        Class<? extends Throwable> throwableClass = (Class<? extends Throwable>) clazz;
        resolvedTypes.add(throwableClass);
      } catch (ClassNotFoundException ex) {
        throw new IllegalArgumentException(
            "Retryable exception type not found: " + configuredType, ex);
      }
    }

    return resolvedTypes;
  }
}
