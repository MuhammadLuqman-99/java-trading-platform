package com.tradingplatform.integration.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class RateLimitRetryExecutorTest {
  @Test
  void shouldRetryRateLimitedCallsUsingRetryAfterHeader() {
    List<Duration> waits = new ArrayList<>();
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:00:00Z"), ZoneOffset.UTC);
    RateLimitRetryExecutor executor =
        new RateLimitRetryExecutor(
            3,
            new RetryAfterParser(clock),
            new JitteredExponentialBackoff(200L, 5000L, false),
            waits::add,
            registry);

    AtomicInteger attempts = new AtomicInteger();
    String actual =
        executor.execute(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.RETRY_AFTER, "1");
                throw new BinanceApiException(429, headers, "{\"code\":-1003}", -1003);
              }
              return "ok";
            });

    assertEquals("ok", actual);
    assertEquals(2, attempts.get());
    assertEquals(List.of(Duration.ofSeconds(1)), waits);
    assertEquals(1.0d, registry.get("connector.binance.rate_limit.retry").counter().count());
  }

  @Test
  void shouldNotRetryNonRateLimitErrors() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RateLimitRetryExecutor executor =
        new RateLimitRetryExecutor(
            3,
            new RetryAfterParser(Clock.systemUTC()),
            new JitteredExponentialBackoff(200L, 5000L, false),
            duration -> {},
            registry);

    AtomicInteger attempts = new AtomicInteger();
    assertThrows(
        BinanceApiException.class,
        () ->
            executor.execute(
                () -> {
                  attempts.incrementAndGet();
                  throw new BinanceApiException(400, HttpHeaders.EMPTY, "{\"code\":-1013}", -1013);
                }));

    assertEquals(1, attempts.get());
    assertEquals(0, registry.find("connector.binance.rate_limit.retry").meters().size());
  }

  @Test
  void shouldStopRetryingWhenAttemptsExhausted() {
    List<Duration> waits = new ArrayList<>();
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RateLimitRetryExecutor executor =
        new RateLimitRetryExecutor(
            2,
            new RetryAfterParser(Clock.systemUTC()),
            new JitteredExponentialBackoff(100L, 500L, false),
            waits::add,
            registry);

    AtomicInteger attempts = new AtomicInteger();
    assertThrows(
        BinanceApiException.class,
        () ->
            executor.execute(
                () -> {
                  attempts.incrementAndGet();
                  throw new BinanceApiException(429, HttpHeaders.EMPTY, "", null);
                }));

    assertEquals(2, attempts.get());
    assertEquals(List.of(Duration.ofMillis(100L)), waits);
    assertEquals(1.0d, registry.get("connector.binance.rate_limit.retry").counter().count());
    assertEquals(1.0d, registry.get("connector.binance.rate_limit.exhausted").counter().count());
  }
}
