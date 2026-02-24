package com.tradingplatform.tradingapi.reconciliation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "reconciliation.balance",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class BalanceReconciliationScheduler {
  private final BalanceReconciliationService balanceReconciliationService;

  public BalanceReconciliationScheduler(BalanceReconciliationService balanceReconciliationService) {
    this.balanceReconciliationService = balanceReconciliationService;
  }

  @Scheduled(fixedDelayString = "${reconciliation.balance.fixed-delay-ms:300000}")
  public void runScheduled() {
    balanceReconciliationService.runOnce();
  }
}
