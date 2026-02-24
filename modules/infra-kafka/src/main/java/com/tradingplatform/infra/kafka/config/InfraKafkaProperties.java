package com.tradingplatform.infra.kafka.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "infra.kafka")
public class InfraKafkaProperties {
  private List<String> bootstrapServers = new ArrayList<>(List.of("localhost:9092"));
  private Producer producer = new Producer();
  private Consumer consumer = new Consumer();
  private Retry retry = new Retry();

  // Legacy fallback keys kept for compatibility.
  private String producerClientId;
  private String consumerGroupId;
  private String autoOffsetReset;
  private Integer producerRetries;
  private Boolean producerIdempotenceEnabled;

  public List<String> getBootstrapServers() {
    return bootstrapServers;
  }

  public void setBootstrapServers(List<String> bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  public Producer getProducer() {
    return producer;
  }

  public void setProducer(Producer producer) {
    this.producer = producer;
  }

  public Consumer getConsumer() {
    return consumer;
  }

  public void setConsumer(Consumer consumer) {
    this.consumer = consumer;
  }

  public Retry getRetry() {
    return retry;
  }

  public void setRetry(Retry retry) {
    this.retry = retry;
  }

  public String getProducerClientId() {
    return producerClientId;
  }

  public void setProducerClientId(String producerClientId) {
    this.producerClientId = producerClientId;
  }

  public String getConsumerGroupId() {
    return consumerGroupId;
  }

  public void setConsumerGroupId(String consumerGroupId) {
    this.consumerGroupId = consumerGroupId;
  }

  public String getAutoOffsetReset() {
    return autoOffsetReset;
  }

  public void setAutoOffsetReset(String autoOffsetReset) {
    this.autoOffsetReset = autoOffsetReset;
  }

  public Integer getProducerRetries() {
    return producerRetries;
  }

  public void setProducerRetries(Integer producerRetries) {
    this.producerRetries = producerRetries;
  }

  public Boolean getProducerIdempotenceEnabled() {
    return producerIdempotenceEnabled;
  }

  public void setProducerIdempotenceEnabled(Boolean producerIdempotenceEnabled) {
    this.producerIdempotenceEnabled = producerIdempotenceEnabled;
  }

  public String bootstrapServersAsCsv() {
    return String.join(",", bootstrapServers);
  }

  public String effectiveProducerClientId() {
    if (hasText(producerClientId)) {
      return producerClientId;
    }
    return producer.getClientId();
  }

  public int effectiveProducerRetries() {
    if (producerRetries != null) {
      return Math.max(0, producerRetries);
    }
    return Math.max(0, producer.getRetries());
  }

  public boolean effectiveProducerIdempotenceEnabled() {
    if (producerIdempotenceEnabled != null) {
      return producerIdempotenceEnabled;
    }
    return producer.isIdempotenceEnabled();
  }

  public String effectiveConsumerGroupId() {
    if (hasText(consumerGroupId)) {
      return consumerGroupId;
    }
    return consumer.getGroupId();
  }

  public String effectiveAutoOffsetReset() {
    if (hasText(autoOffsetReset)) {
      return autoOffsetReset;
    }
    return consumer.getAutoOffsetReset();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public static class Producer {
    private String clientId = "trading-platform-producer";
    private String acks = "all";
    private boolean idempotenceEnabled = true;
    private int retries = 3;
    private String compressionType = "lz4";
    private int lingerMs = 5;
    private int batchSize = 32768;
    private int deliveryTimeoutMs = 120000;
    private int requestTimeoutMs = 30000;
    private int maxInFlightRequestsPerConnection = 5;
    private long sendTimeoutMs = 0L;

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public String getAcks() {
      return acks;
    }

    public void setAcks(String acks) {
      this.acks = acks;
    }

    public boolean isIdempotenceEnabled() {
      return idempotenceEnabled;
    }

    public void setIdempotenceEnabled(boolean idempotenceEnabled) {
      this.idempotenceEnabled = idempotenceEnabled;
    }

    public int getRetries() {
      return retries;
    }

    public void setRetries(int retries) {
      this.retries = retries;
    }

    public String getCompressionType() {
      return compressionType;
    }

    public void setCompressionType(String compressionType) {
      this.compressionType = compressionType;
    }

    public int getLingerMs() {
      return lingerMs;
    }

    public void setLingerMs(int lingerMs) {
      this.lingerMs = lingerMs;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }

    public int getDeliveryTimeoutMs() {
      return deliveryTimeoutMs;
    }

    public void setDeliveryTimeoutMs(int deliveryTimeoutMs) {
      this.deliveryTimeoutMs = deliveryTimeoutMs;
    }

    public int getRequestTimeoutMs() {
      return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
      this.requestTimeoutMs = requestTimeoutMs;
    }

    public int getMaxInFlightRequestsPerConnection() {
      return maxInFlightRequestsPerConnection;
    }

    public void setMaxInFlightRequestsPerConnection(int maxInFlightRequestsPerConnection) {
      this.maxInFlightRequestsPerConnection = maxInFlightRequestsPerConnection;
    }

    public long getSendTimeoutMs() {
      return sendTimeoutMs;
    }

    public void setSendTimeoutMs(long sendTimeoutMs) {
      this.sendTimeoutMs = sendTimeoutMs;
    }
  }

  public static class Consumer {
    private String groupId = "cg-default";
    private String autoOffsetReset = "earliest";
    private boolean enableAutoCommit = false;
    private int maxPollRecords = 500;
    private int maxPollIntervalMs = 300000;
    private int sessionTimeoutMs = 10000;
    private int heartbeatIntervalMs = 3000;
    private int fetchMinBytes = 1;
    private int fetchMaxWaitMs = 500;
    private int concurrency = 1;

    public String getGroupId() {
      return groupId;
    }

    public void setGroupId(String groupId) {
      this.groupId = groupId;
    }

    public String getAutoOffsetReset() {
      return autoOffsetReset;
    }

    public void setAutoOffsetReset(String autoOffsetReset) {
      this.autoOffsetReset = autoOffsetReset;
    }

    public boolean isEnableAutoCommit() {
      return enableAutoCommit;
    }

    public void setEnableAutoCommit(boolean enableAutoCommit) {
      this.enableAutoCommit = enableAutoCommit;
    }

    public int getMaxPollRecords() {
      return maxPollRecords;
    }

    public void setMaxPollRecords(int maxPollRecords) {
      this.maxPollRecords = maxPollRecords;
    }

    public int getMaxPollIntervalMs() {
      return maxPollIntervalMs;
    }

    public void setMaxPollIntervalMs(int maxPollIntervalMs) {
      this.maxPollIntervalMs = maxPollIntervalMs;
    }

    public int getSessionTimeoutMs() {
      return sessionTimeoutMs;
    }

    public void setSessionTimeoutMs(int sessionTimeoutMs) {
      this.sessionTimeoutMs = sessionTimeoutMs;
    }

    public int getHeartbeatIntervalMs() {
      return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(int heartbeatIntervalMs) {
      this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public int getFetchMinBytes() {
      return fetchMinBytes;
    }

    public void setFetchMinBytes(int fetchMinBytes) {
      this.fetchMinBytes = fetchMinBytes;
    }

    public int getFetchMaxWaitMs() {
      return fetchMaxWaitMs;
    }

    public void setFetchMaxWaitMs(int fetchMaxWaitMs) {
      this.fetchMaxWaitMs = fetchMaxWaitMs;
    }

    public int getConcurrency() {
      return concurrency;
    }

    public void setConcurrency(int concurrency) {
      this.concurrency = concurrency;
    }
  }

  public static class Retry {
    private String mode = "fixed";
    private int maxAttempts = 1;
    private long fixedBackoffMs = 0L;
    private long initialBackoffMs = 100L;
    private long maxBackoffMs = 10000L;
    private double multiplier = 2.0d;
    private List<String> retryableExceptions = new ArrayList<>();

    public String getMode() {
      return mode;
    }

    public void setMode(String mode) {
      this.mode = mode;
    }

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public long getFixedBackoffMs() {
      return fixedBackoffMs;
    }

    public void setFixedBackoffMs(long fixedBackoffMs) {
      this.fixedBackoffMs = fixedBackoffMs;
    }

    public long getInitialBackoffMs() {
      return initialBackoffMs;
    }

    public void setInitialBackoffMs(long initialBackoffMs) {
      this.initialBackoffMs = initialBackoffMs;
    }

    public long getMaxBackoffMs() {
      return maxBackoffMs;
    }

    public void setMaxBackoffMs(long maxBackoffMs) {
      this.maxBackoffMs = maxBackoffMs;
    }

    public double getMultiplier() {
      return multiplier;
    }

    public void setMultiplier(double multiplier) {
      this.multiplier = multiplier;
    }

    public List<String> getRetryableExceptions() {
      return retryableExceptions;
    }

    public void setRetryableExceptions(List<String> retryableExceptions) {
      this.retryableExceptions = retryableExceptions;
    }
  }
}
