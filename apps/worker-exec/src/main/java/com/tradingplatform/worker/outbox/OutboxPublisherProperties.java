package com.tradingplatform.worker.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "outbox.publisher")
public class OutboxPublisherProperties {
  private boolean enabled = true;
  private int batchSize = 100;
  private long fixedDelayMs = 1_000L;
  private String producerName = "worker-exec-outbox-publisher";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public long getFixedDelayMs() {
    return fixedDelayMs;
  }

  public void setFixedDelayMs(long fixedDelayMs) {
    this.fixedDelayMs = fixedDelayMs;
  }

  public String getProducerName() {
    return producerName;
  }

  public void setProducerName(String producerName) {
    this.producerName = producerName;
  }
}
