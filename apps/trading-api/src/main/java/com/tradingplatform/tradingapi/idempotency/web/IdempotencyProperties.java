package com.tradingplatform.tradingapi.idempotency.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "idempotency")
public class IdempotencyProperties {
  private boolean enabled = true;
  private String requiredHeader = "Idempotency-Key";
  private long ttlHours = 24L;
  private List<String> optInPaths = new ArrayList<>(List.of("/v1/orders/**"));

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getRequiredHeader() {
    return requiredHeader;
  }

  public void setRequiredHeader(String requiredHeader) {
    this.requiredHeader = requiredHeader;
  }

  public long getTtlHours() {
    return ttlHours;
  }

  public void setTtlHours(long ttlHours) {
    this.ttlHours = ttlHours;
  }

  public List<String> getOptInPaths() {
    return optInPaths;
  }

  public void setOptInPaths(List<String> optInPaths) {
    this.optInPaths = optInPaths;
  }
}
