package com.tradingplatform.infra.kafka.errors;

import java.time.Duration;
import java.util.Objects;

public class FixedBackoffRetryPolicy implements RetryPolicy {
    private final int maxAttempts;
    private final Duration backoff;

    public FixedBackoffRetryPolicy(int maxAttempts, Duration backoff) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoff = Objects.requireNonNull(backoff, "backoff must not be null");
    }

    @Override
    public boolean shouldRetry(int attempt, Exception exception) {
        return attempt < maxAttempts;
    }

    @Override
    public Duration backoffForAttempt(int attempt) {
        return backoff;
    }
}
