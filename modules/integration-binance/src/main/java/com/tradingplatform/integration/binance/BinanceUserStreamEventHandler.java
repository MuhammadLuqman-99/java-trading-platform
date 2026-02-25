package com.tradingplatform.integration.binance;

import java.time.Duration;

public interface BinanceUserStreamEventHandler {
  default void onConnected(String listenKey) {}

  default void onDisconnected(int statusCode, String reason) {}

  default void onReconnectScheduled(long reconnectAttempts, Duration delay) {}

  default void onExecutionReport(BinanceExecutionReportEvent event) {}

  default void onError(String errorCode, String errorMessage, Throwable error) {}

  static BinanceUserStreamEventHandler noop() {
    return new BinanceUserStreamEventHandler() {};
  }
}
