package com.tradingplatform.worker.connector;

public enum FillProcessingOutcome {
  INSERTED("inserted"),
  DUPLICATE("duplicate"),
  UNMAPPED("unmapped");

  private final String metricTag;

  FillProcessingOutcome(String metricTag) {
    this.metricTag = metricTag;
  }

  public String metricTag() {
    return metricTag;
  }
}
