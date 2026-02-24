package com.tradingplatform.tradingapi.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingReconciliationReporter implements ReconciliationReporter {
  private static final Logger log = LoggerFactory.getLogger(LoggingReconciliationReporter.class);

  @Override
  public void report(ReconciliationResult result) {
    log.info(
        "Balance reconciliation status={} drift_assets={} notes={}",
        result.status(),
        result.driftByAsset().size(),
        result.notes());
  }
}
