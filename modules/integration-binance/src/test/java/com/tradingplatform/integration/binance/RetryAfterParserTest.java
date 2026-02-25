package com.tradingplatform.integration.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class RetryAfterParserTest {
  @Test
  void shouldParseRetryAfterSeconds() {
    RetryAfterParser parser = new RetryAfterParser(Clock.systemUTC());

    Duration actual = parser.parse("5").orElseThrow();

    assertEquals(Duration.ofSeconds(5), actual);
  }

  @Test
  void shouldParseRetryAfterHttpDate() {
    Instant now = Instant.parse("2026-02-25T12:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    RetryAfterParser parser = new RetryAfterParser(clock);
    String header =
        DateTimeFormatter.RFC_1123_DATE_TIME.format(now.plusSeconds(3).atZone(ZoneOffset.UTC));

    Duration actual = parser.parse(header).orElseThrow();

    assertEquals(Duration.ofSeconds(3), actual);
  }

  @Test
  void shouldReturnEmptyWhenHeaderInvalid() {
    RetryAfterParser parser = new RetryAfterParser(Clock.systemUTC());

    assertTrue(parser.parse("not-a-date").isEmpty());
  }
}
