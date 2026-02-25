package com.tradingplatform.integration.binance;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connector.binance")
public class BinanceConnectorProperties {
  private String baseUrl = "https://api.binance.com";
  private String apiKey = "";
  private String apiSecret = "";
  private String apiKeyFile = "";
  private String apiSecretFile = "";
  private long recvWindowMs = 5000L;
  private long timeoutMs = 3000L;
  private Retry retry = new Retry();
  private StatusMapping statusMapping = new StatusMapping();

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getApiSecret() {
    return apiSecret;
  }

  public void setApiSecret(String apiSecret) {
    this.apiSecret = apiSecret;
  }

  public String getApiKeyFile() {
    return apiKeyFile;
  }

  public void setApiKeyFile(String apiKeyFile) {
    this.apiKeyFile = apiKeyFile;
  }

  public String getApiSecretFile() {
    return apiSecretFile;
  }

  public void setApiSecretFile(String apiSecretFile) {
    this.apiSecretFile = apiSecretFile;
  }

  public long getRecvWindowMs() {
    return recvWindowMs;
  }

  public void setRecvWindowMs(long recvWindowMs) {
    this.recvWindowMs = recvWindowMs;
  }

  public long getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(long timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  public Retry getRetry() {
    return retry;
  }

  public void setRetry(Retry retry) {
    this.retry = retry;
  }

  public StatusMapping getStatusMapping() {
    return statusMapping;
  }

  public void setStatusMapping(StatusMapping statusMapping) {
    this.statusMapping = statusMapping;
  }

  public static class Retry {
    private int maxAttempts = 5;
    private long baseBackoffMs = 200L;
    private long maxBackoffMs = 5000L;
    private boolean jitterEnabled = true;

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public long getBaseBackoffMs() {
      return baseBackoffMs;
    }

    public void setBaseBackoffMs(long baseBackoffMs) {
      this.baseBackoffMs = baseBackoffMs;
    }

    public long getMaxBackoffMs() {
      return maxBackoffMs;
    }

    public void setMaxBackoffMs(long maxBackoffMs) {
      this.maxBackoffMs = maxBackoffMs;
    }

    public boolean isJitterEnabled() {
      return jitterEnabled;
    }

    public void setJitterEnabled(boolean jitterEnabled) {
      this.jitterEnabled = jitterEnabled;
    }
  }

  public static class StatusMapping {
    private String venue = BinanceVenue.BINANCE_SPOT;
    private boolean refreshEnabled = false;
    private long refreshIntervalMs = 300000L;

    public String getVenue() {
      return venue;
    }

    public void setVenue(String venue) {
      this.venue = venue;
    }

    public boolean isRefreshEnabled() {
      return refreshEnabled;
    }

    public void setRefreshEnabled(boolean refreshEnabled) {
      this.refreshEnabled = refreshEnabled;
    }

    public long getRefreshIntervalMs() {
      return refreshIntervalMs;
    }

    public void setRefreshIntervalMs(long refreshIntervalMs) {
      this.refreshIntervalMs = refreshIntervalMs;
    }
  }
}
