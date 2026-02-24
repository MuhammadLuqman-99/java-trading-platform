package com.tradingplatform.tradingapi.reconciliation;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StubBalanceReconciliationGateway implements BalanceReconciliationGateway {
  @Override
  public Map<String, BigDecimal> fetchExternalBalances() {
    return Map.of();
  }
}
