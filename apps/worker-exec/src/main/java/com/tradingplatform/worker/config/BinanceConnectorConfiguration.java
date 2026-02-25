package com.tradingplatform.worker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.integration.binance.BinanceConnectorProperties;
import com.tradingplatform.integration.binance.BinanceOrderGateway;
import com.tradingplatform.integration.binance.BinanceRequestSigner;
import com.tradingplatform.integration.binance.DatabaseBackedExchangeOrderStatusMapper;
import com.tradingplatform.integration.binance.ExchangeOrderStatusMappingRefreshTask;
import com.tradingplatform.integration.binance.ExchangeOrderStatusMappingRepository;
import com.tradingplatform.integration.binance.JdbcExchangeOrderStatusMappingRepository;
import com.tradingplatform.integration.binance.JitteredExponentialBackoff;
import com.tradingplatform.integration.binance.RateLimitRetryExecutor;
import com.tradingplatform.integration.binance.RestBinanceOrderGateway;
import com.tradingplatform.integration.binance.RetryAfterParser;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(BinanceConnectorProperties.class)
public class BinanceConnectorConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Clock binanceConnectorClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  public ExchangeOrderStatusMappingRepository exchangeOrderStatusMappingRepository(
      JdbcTemplate jdbcTemplate) {
    return new JdbcExchangeOrderStatusMappingRepository(jdbcTemplate);
  }

  @Bean
  @ConditionalOnMissingBean
  public DatabaseBackedExchangeOrderStatusMapper exchangeOrderStatusMapper(
      ExchangeOrderStatusMappingRepository repository, MeterRegistry meterRegistry) {
    return new DatabaseBackedExchangeOrderStatusMapper(repository, meterRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  public BinanceRequestSigner binanceRequestSigner(
      BinanceConnectorProperties properties, Clock binanceConnectorClock) {
    String apiSecret =
        resolveOptionalSecret(
            properties.getApiSecret(),
            properties.getApiSecretFile(),
            "connector.binance.api-secret-file");
    return new BinanceRequestSigner(
        apiSecret, properties.getRecvWindowMs(), binanceConnectorClock);
  }

  @Bean
  @ConditionalOnMissingBean
  public RetryAfterParser retryAfterParser(Clock binanceConnectorClock) {
    return new RetryAfterParser(binanceConnectorClock);
  }

  @Bean
  @ConditionalOnMissingBean
  public JitteredExponentialBackoff jitteredExponentialBackoff(BinanceConnectorProperties properties) {
    BinanceConnectorProperties.Retry retry = properties.getRetry();
    return new JitteredExponentialBackoff(
        retry.getBaseBackoffMs(), retry.getMaxBackoffMs(), retry.isJitterEnabled());
  }

  @Bean
  @ConditionalOnMissingBean
  public RateLimitRetryExecutor rateLimitRetryExecutor(
      BinanceConnectorProperties properties,
      RetryAfterParser retryAfterParser,
      JitteredExponentialBackoff jitteredExponentialBackoff,
      MeterRegistry meterRegistry) {
    return new RateLimitRetryExecutor(
        properties.getRetry().getMaxAttempts(),
        retryAfterParser,
        jitteredExponentialBackoff,
        meterRegistry);
  }

  @Bean
  @ConditionalOnMissingBean(name = "binanceRestClient")
  public RestClient binanceRestClient(BinanceConnectorProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    int timeout = (int) Math.min(Integer.MAX_VALUE, Math.max(100L, properties.getTimeoutMs()));
    requestFactory.setConnectTimeout(timeout);
    requestFactory.setReadTimeout(timeout);
    return RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(requestFactory).build();
  }

  @Bean
  @ConditionalOnMissingBean
  public BinanceOrderGateway binanceOrderGateway(
      RestClient binanceRestClient,
      ObjectMapper objectMapper,
      BinanceConnectorProperties properties,
      DatabaseBackedExchangeOrderStatusMapper exchangeOrderStatusMapper,
      BinanceRequestSigner binanceRequestSigner,
      RateLimitRetryExecutor rateLimitRetryExecutor) {
    properties.setApiKey(
        resolveOptionalSecret(
            properties.getApiKey(), properties.getApiKeyFile(), "connector.binance.api-key-file"));
    properties.setApiSecret(
        resolveOptionalSecret(
            properties.getApiSecret(),
            properties.getApiSecretFile(),
            "connector.binance.api-secret-file"));
    return new RestBinanceOrderGateway(
        binanceRestClient,
        objectMapper,
        properties,
        exchangeOrderStatusMapper,
        binanceRequestSigner,
        rateLimitRetryExecutor);
  }

  @Bean
  @ConditionalOnMissingBean
  public ExchangeOrderStatusMappingRefreshTask exchangeOrderStatusMappingRefreshTask(
      DatabaseBackedExchangeOrderStatusMapper mapper, BinanceConnectorProperties properties) {
    return new ExchangeOrderStatusMappingRefreshTask(mapper, properties);
  }

  private static String resolveOptionalSecret(String value, String filePath, String propertyName) {
    if (filePath == null || filePath.isBlank()) {
      return value;
    }
    try {
      String fromFile = Files.readString(Path.of(filePath), StandardCharsets.UTF_8).trim();
      return fromFile.isBlank() ? value : fromFile;
    } catch (IOException ex) {
      throw new IllegalArgumentException(propertyName + " cannot be read: " + filePath, ex);
    }
  }
}
