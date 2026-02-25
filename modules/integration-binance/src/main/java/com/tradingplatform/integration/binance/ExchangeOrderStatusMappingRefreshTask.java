package com.tradingplatform.integration.binance;

import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;

public class ExchangeOrderStatusMappingRefreshTask {
  private final DatabaseBackedExchangeOrderStatusMapper mapper;
  private final BinanceConnectorProperties properties;

  public ExchangeOrderStatusMappingRefreshTask(
      DatabaseBackedExchangeOrderStatusMapper mapper, BinanceConnectorProperties properties) {
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  @Scheduled(
      fixedDelayString = "${connector.binance.status-mapping.refresh-interval-ms:300000}",
      initialDelayString = "${connector.binance.status-mapping.refresh-interval-ms:300000}")
  public void refreshMappings() {
    if (!properties.getStatusMapping().isRefreshEnabled()) {
      return;
    }
    mapper.refresh();
  }
}
