package com.tradingplatform.integration.binance;

import java.util.Optional;

public interface BinanceExecutionReportParser {
  Optional<BinanceExecutionReport> parse(String rawPayload);
}
