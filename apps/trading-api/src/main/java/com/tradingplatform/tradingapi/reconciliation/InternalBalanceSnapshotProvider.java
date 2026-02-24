package com.tradingplatform.tradingapi.reconciliation;

import java.math.BigDecimal;
import java.util.Map;

public interface InternalBalanceSnapshotProvider {
  Map<String, BigDecimal> computeInternalTotals();
}
