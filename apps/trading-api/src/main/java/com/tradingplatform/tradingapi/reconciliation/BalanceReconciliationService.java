package com.tradingplatform.tradingapi.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BalanceReconciliationService {
  private final BalanceReconciliationGateway gateway;
  private final InternalBalanceSnapshotProvider internalSnapshotProvider;
  private final ReconciliationReporter reporter;

  public BalanceReconciliationService(
      BalanceReconciliationGateway gateway,
      InternalBalanceSnapshotProvider internalSnapshotProvider,
      ReconciliationReporter reporter) {
    this.gateway = gateway;
    this.internalSnapshotProvider = internalSnapshotProvider;
    this.reporter = reporter;
  }

  public ReconciliationResult runOnce() {
    Instant startedAt = Instant.now();
    Map<String, BigDecimal> external = gateway.fetchExternalBalances();
    Map<String, BigDecimal> internal = internalSnapshotProvider.computeInternalTotals();
    Map<String, BigDecimal> driftByAsset = computeDrift(internal, external);
    ReconciliationResult result =
        new ReconciliationResult(
            startedAt,
            Instant.now(),
            ReconciliationStatus.NOT_IMPLEMENTED,
            driftByAsset,
            "Scaffold only: external integration/remediation not implemented yet");
    reporter.report(result);
    return result;
  }

  private static Map<String, BigDecimal> computeDrift(
      Map<String, BigDecimal> internal, Map<String, BigDecimal> external) {
    Map<String, BigDecimal> drift = new LinkedHashMap<>();
    for (Map.Entry<String, BigDecimal> entry : internal.entrySet()) {
      String asset = entry.getKey();
      BigDecimal externalValue = external.getOrDefault(asset, BigDecimal.ZERO);
      drift.put(asset, entry.getValue().subtract(externalValue));
    }
    for (Map.Entry<String, BigDecimal> entry : external.entrySet()) {
      if (!drift.containsKey(entry.getKey())) {
        drift.put(entry.getKey(), BigDecimal.ZERO.subtract(entry.getValue()));
      }
    }
    return drift;
  }
}
