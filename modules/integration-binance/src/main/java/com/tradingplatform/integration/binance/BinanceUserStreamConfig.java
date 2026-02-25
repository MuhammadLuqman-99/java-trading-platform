package com.tradingplatform.integration.binance;

import java.net.URI;
import java.time.Duration;

public record BinanceUserStreamConfig(
    URI wsBaseUri,
    Duration keepAliveInterval,
    Duration reconnectBaseBackoff,
    Duration reconnectMaxBackoff,
    Duration stableConnectionReset) {
  public BinanceUserStreamConfig {
    if (wsBaseUri == null) {
      throw new IllegalArgumentException("wsBaseUri is required");
    }
    if (keepAliveInterval == null || keepAliveInterval.isNegative() || keepAliveInterval.isZero()) {
      throw new IllegalArgumentException("keepAliveInterval must be > 0");
    }
    if (reconnectBaseBackoff == null
        || reconnectBaseBackoff.isNegative()
        || reconnectBaseBackoff.isZero()) {
      throw new IllegalArgumentException("reconnectBaseBackoff must be > 0");
    }
    if (reconnectMaxBackoff == null
        || reconnectMaxBackoff.isNegative()
        || reconnectMaxBackoff.isZero()) {
      throw new IllegalArgumentException("reconnectMaxBackoff must be > 0");
    }
    if (reconnectMaxBackoff.compareTo(reconnectBaseBackoff) < 0) {
      throw new IllegalArgumentException("reconnectMaxBackoff must be >= reconnectBaseBackoff");
    }
    if (stableConnectionReset == null
        || stableConnectionReset.isNegative()
        || stableConnectionReset.isZero()) {
      throw new IllegalArgumentException("stableConnectionReset must be > 0");
    }
  }
}
