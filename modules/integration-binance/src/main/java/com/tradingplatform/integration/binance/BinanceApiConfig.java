package com.tradingplatform.integration.binance;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

public record BinanceApiConfig(
    URI baseUri,
    String apiKey,
    String apiSecret,
    long recvWindowMs,
    Duration timeout,
    Clock clock) {
  public BinanceApiConfig {
    if (baseUri == null) {
      throw new IllegalArgumentException("baseUri is required");
    }
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("apiKey is required");
    }
    if (apiSecret == null || apiSecret.isBlank()) {
      throw new IllegalArgumentException("apiSecret is required");
    }
    if (recvWindowMs <= 0) {
      throw new IllegalArgumentException("recvWindowMs must be > 0");
    }
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("timeout must be > 0");
    }
    if (clock == null) {
      throw new IllegalArgumentException("clock is required");
    }
  }
}
