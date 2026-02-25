package com.tradingplatform.worker.execution;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connector.binance")
public class BinanceConnectorProperties {
  private boolean enabled = false;
  private String baseUrl = "https://testnet.binance.vision";
  private String apiKey = "";
  private String apiSecret = "";
  private String apiKeyFile = "";
  private String apiSecretFile = "";
  private long recvWindowMs = 5000L;
  private Duration timeout = Duration.ofSeconds(5);
  private Catchup catchup = new Catchup();
  private Retry retry = new Retry();
  private Health health = new Health();
  private Ws ws = new Ws();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

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

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }

  public Catchup getCatchup() {
    return catchup;
  }

  public void setCatchup(Catchup catchup) {
    this.catchup = catchup;
  }

  public Retry getRetry() {
    return retry;
  }

  public void setRetry(Retry retry) {
    this.retry = retry;
  }

  public Health getHealth() {
    return health;
  }

  public void setHealth(Health health) {
    this.health = health;
  }

  public Ws getWs() {
    return ws;
  }

  public void setWs(Ws ws) {
    this.ws = ws;
  }

  public static class Catchup {
    private long fixedDelayMs = 30000L;
    private long recentTradesLookbackMinutes = 30L;
    private long replayPollDelayMs = 5000L;
    private long recoveryDedupeWindowMs = 120000L;

    public long getFixedDelayMs() {
      return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
      this.fixedDelayMs = fixedDelayMs;
    }

    public long getRecentTradesLookbackMinutes() {
      return recentTradesLookbackMinutes;
    }

    public void setRecentTradesLookbackMinutes(long recentTradesLookbackMinutes) {
      this.recentTradesLookbackMinutes = recentTradesLookbackMinutes;
    }

    public long getReplayPollDelayMs() {
      return replayPollDelayMs;
    }

    public void setReplayPollDelayMs(long replayPollDelayMs) {
      this.replayPollDelayMs = replayPollDelayMs;
    }

    public long getRecoveryDedupeWindowMs() {
      return recoveryDedupeWindowMs;
    }

    public void setRecoveryDedupeWindowMs(long recoveryDedupeWindowMs) {
      this.recoveryDedupeWindowMs = recoveryDedupeWindowMs;
    }
  }

  public static class Retry {
    private int maxAttempts = 3;
    private Duration baseBackoff = Duration.ofMillis(250);
    private Duration maxBackoff = Duration.ofSeconds(3);
    private boolean jitterEnabled = true;

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public Duration getBaseBackoff() {
      return baseBackoff;
    }

    public void setBaseBackoff(Duration baseBackoff) {
      this.baseBackoff = baseBackoff;
    }

    public Duration getMaxBackoff() {
      return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
      this.maxBackoff = maxBackoff;
    }

    public boolean isJitterEnabled() {
      return jitterEnabled;
    }

    public void setJitterEnabled(boolean jitterEnabled) {
      this.jitterEnabled = jitterEnabled;
    }
  }

  public static class Health {
    private long downThresholdMinutes = 5L;

    public long getDownThresholdMinutes() {
      return downThresholdMinutes;
    }

    public void setDownThresholdMinutes(long downThresholdMinutes) {
      this.downThresholdMinutes = downThresholdMinutes;
    }
  }

  public static class Ws {
    private boolean enabled = false;
    private String baseUrl = "wss://stream.binance.com:9443";
    private long reconnectBaseBackoffMs = 250L;
    private long reconnectMaxBackoffMs = 30000L;
    private long stableResetSeconds = 60L;
    private long keepaliveIntervalSeconds = 1500L;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public long getReconnectBaseBackoffMs() {
      return reconnectBaseBackoffMs;
    }

    public void setReconnectBaseBackoffMs(long reconnectBaseBackoffMs) {
      this.reconnectBaseBackoffMs = reconnectBaseBackoffMs;
    }

    public long getReconnectMaxBackoffMs() {
      return reconnectMaxBackoffMs;
    }

    public void setReconnectMaxBackoffMs(long reconnectMaxBackoffMs) {
      this.reconnectMaxBackoffMs = reconnectMaxBackoffMs;
    }

    public long getStableResetSeconds() {
      return stableResetSeconds;
    }

    public void setStableResetSeconds(long stableResetSeconds) {
      this.stableResetSeconds = stableResetSeconds;
    }

    public long getKeepaliveIntervalSeconds() {
      return keepaliveIntervalSeconds;
    }

    public void setKeepaliveIntervalSeconds(long keepaliveIntervalSeconds) {
      this.keepaliveIntervalSeconds = keepaliveIntervalSeconds;
    }
  }
}
