package com.tradingplatform.worker.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.integration.binance.BinanceApiConfig;
import com.tradingplatform.integration.binance.BinanceRequestSigner;
import com.tradingplatform.integration.binance.BinanceUserStreamClient;
import com.tradingplatform.integration.binance.BinanceUserStreamConfig;
import com.tradingplatform.integration.binance.HttpBinanceOrderClient;
import com.tradingplatform.integration.binance.HttpBinanceUserStreamClient;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BinanceConnectorProperties.class)
public class ExecutionAdapterConfiguration {
  @Bean
  @ConditionalOnProperty(prefix = "worker.execution", name = "adapter", havingValue = "binance")
  HttpBinanceOrderClient binanceOrderClient(
      BinanceConnectorProperties properties, ObjectMapper objectMapper) {
    String apiKey =
        resolveSecret(
            properties.getApiKey(),
            properties.getApiKeyFile(),
            "connector.binance.api-key",
            "connector.binance.api-key-file");
    String apiSecret =
        resolveSecret(
            properties.getApiSecret(),
            properties.getApiSecretFile(),
            "connector.binance.api-secret",
            "connector.binance.api-secret-file");
    BinanceApiConfig config = buildApiConfig(properties, apiKey, apiSecret);
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
    BinanceRequestSigner signer =
        new BinanceRequestSigner(apiSecret, properties.getRecvWindowMs(), config.clock());
    return new HttpBinanceOrderClient(
        httpClient,
        objectMapper,
        config,
        signer,
        properties.getRetry().getMaxAttempts(),
        properties.getRetry().getBaseBackoff(),
        properties.getRetry().getMaxBackoff(),
        properties.getRetry().isJitterEnabled());
  }

  @Bean
  @ConditionalOnProperty(prefix = "worker.execution", name = "adapter", havingValue = "binance")
  @ConditionalOnProperty(prefix = "connector.binance", name = "enabled", havingValue = "true")
  @ConditionalOnProperty(prefix = "connector.binance.ws", name = "enabled", havingValue = "true")
  BinanceUserStreamClient binanceUserStreamClient(
      BinanceConnectorProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
    String apiKey =
        resolveSecret(
            properties.getApiKey(),
            properties.getApiKeyFile(),
            "connector.binance.api-key",
            "connector.binance.api-key-file");
    String apiSecret =
        resolveSecret(
            properties.getApiSecret(),
            properties.getApiSecretFile(),
            "connector.binance.api-secret",
            "connector.binance.api-secret-file");
    BinanceApiConfig config = buildApiConfig(properties, apiKey, apiSecret);
    BinanceConnectorProperties.Ws ws = properties.getWs();
    BinanceUserStreamConfig streamConfig =
        new BinanceUserStreamConfig(
            URI.create(ws.getBaseUrl()),
            Duration.ofSeconds(Math.max(30L, ws.getKeepaliveIntervalSeconds())),
            Duration.ofMillis(Math.max(50L, ws.getReconnectBaseBackoffMs())),
            Duration.ofMillis(Math.max(ws.getReconnectBaseBackoffMs(), ws.getReconnectMaxBackoffMs())),
            Duration.ofSeconds(Math.max(10L, ws.getStableResetSeconds())));
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
    return new HttpBinanceUserStreamClient(
        httpClient, objectMapper, config, streamConfig, meterRegistry);
  }

  @Bean
  @ConditionalOnProperty(prefix = "worker.execution", name = "adapter", havingValue = "binance")
  ExecutionOrderAdapter binanceExecutionOrderAdapter(HttpBinanceOrderClient binanceOrderClient) {
    return new BinanceExecutionOrderAdapter(binanceOrderClient);
  }

  @Bean
  @ConditionalOnMissingBean(ExecutionOrderAdapter.class)
  ExecutionOrderAdapter loggingExecutionOrderAdapter() {
    return new LoggingExecutionOrderAdapter();
  }

  private static BinanceApiConfig buildApiConfig(
      BinanceConnectorProperties properties, String apiKey, String apiSecret) {
    return new BinanceApiConfig(
        URI.create(properties.getBaseUrl()),
        apiKey,
        apiSecret,
        properties.getRecvWindowMs(),
        properties.getTimeout(),
        Clock.systemUTC());
  }

  private static String resolveSecret(
      String directValue, String filePath, String directName, String fileName) {
    if (filePath != null && !filePath.isBlank()) {
      String fromFile = readSecret(filePath, fileName);
      if (!fromFile.isBlank()) {
        return fromFile;
      }
      throw new IllegalArgumentException(fileName + " points to an empty file");
    }
    return requiredValue(directValue, directName);
  }

  private static String readSecret(String filePath, String fileName) {
    try {
      return Files.readString(Path.of(filePath), StandardCharsets.UTF_8).trim();
    } catch (IOException ex) {
      throw new IllegalArgumentException(fileName + " cannot be read: " + filePath, ex);
    }
  }

  private static String requiredValue(String value, String propertyName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(propertyName + " must be configured");
    }
    return value;
  }
}
