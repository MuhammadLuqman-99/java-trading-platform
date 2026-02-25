package com.tradingplatform.worker.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.orders.OrderStatus;
import com.tradingplatform.integration.binance.BinanceOrderGateway;
import com.tradingplatform.integration.binance.BinanceVenue;
import com.tradingplatform.integration.binance.ExchangeOrderStatusMapping;
import com.tradingplatform.integration.binance.ExchangeOrderStatusMappingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BinanceConnectorConfigurationTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(BinanceConnectorConfiguration.class)
          .withBean(ObjectMapper.class, ObjectMapper::new)
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
          .withBean(
              ExchangeOrderStatusMappingRepository.class,
              () ->
                  () ->
                      List.of(
                          new ExchangeOrderStatusMapping(
                              BinanceVenue.BINANCE_SPOT,
                              "NEW",
                              OrderStatus.NEW,
                              false,
                              true)))
          .withPropertyValues(
              "connector.binance.base-url=https://binance.test",
              "connector.binance.api-key=api-key",
              "connector.binance.api-secret=api-secret");

  @Test
  void shouldRegisterBinanceGatewayBeans() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(BinanceOrderGateway.class);
          assertThat(context).hasBean("binanceRestClient");
          assertThat(context).hasBean("rateLimitRetryExecutor");
          assertThat(context).hasBean("exchangeOrderStatusMapper");
        });
  }
}
