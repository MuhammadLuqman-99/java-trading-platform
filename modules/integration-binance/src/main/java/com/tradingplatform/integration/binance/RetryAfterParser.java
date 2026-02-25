package com.tradingplatform.integration.binance;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

public class RetryAfterParser {
  private final Clock clock;

  public RetryAfterParser(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public Optional<Duration> parse(String headerValue) {
    if (headerValue == null || headerValue.isBlank()) {
      return Optional.empty();
    }
    String candidate = headerValue.trim();

    try {
      long seconds = Long.parseLong(candidate);
      return Optional.of(Duration.ofSeconds(Math.max(0L, seconds)));
    } catch (NumberFormatException ignored) {
      // Continue with RFC1123 parsing.
    }

    try {
      ZonedDateTime retryAt = ZonedDateTime.parse(candidate, DateTimeFormatter.RFC_1123_DATE_TIME);
      Duration between = Duration.between(clock.instant(), retryAt.toInstant());
      if (between.isNegative()) {
        return Optional.of(Duration.ZERO);
      }
      return Optional.of(between);
    } catch (DateTimeParseException ignored) {
      return Optional.empty();
    }
  }
}
