package com.tradingplatform.tradingapi.ratelimit;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {
  private boolean enabled = false;
  private int maxRequests = 50;
  private int windowSeconds = 60;
  private List<String> optInPaths = new ArrayList<>(List.of("/v1/orders/**"));
  private String keyPrefix = "rate";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getMaxRequests() {
    return maxRequests;
  }

  public void setMaxRequests(int maxRequests) {
    this.maxRequests = maxRequests;
  }

  public int getWindowSeconds() {
    return windowSeconds;
  }

  public void setWindowSeconds(int windowSeconds) {
    this.windowSeconds = windowSeconds;
  }

  public List<String> getOptInPaths() {
    return optInPaths;
  }

  public void setOptInPaths(List<String> optInPaths) {
    this.optInPaths = optInPaths;
  }

  public String getKeyPrefix() {
    return keyPrefix;
  }

  public void setKeyPrefix(String keyPrefix) {
    this.keyPrefix = keyPrefix;
  }
}
