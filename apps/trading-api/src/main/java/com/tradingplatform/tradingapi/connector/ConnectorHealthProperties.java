package com.tradingplatform.tradingapi.connector;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "connector.health")
public class ConnectorHealthProperties {
  private long staleThresholdMinutes = 5L;

  public long getStaleThresholdMinutes() {
    return staleThresholdMinutes;
  }

  public void setStaleThresholdMinutes(long staleThresholdMinutes) {
    this.staleThresholdMinutes = staleThresholdMinutes;
  }
}
