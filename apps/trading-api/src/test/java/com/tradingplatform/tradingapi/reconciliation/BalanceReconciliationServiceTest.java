package com.tradingplatform.tradingapi.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BalanceReconciliationServiceTest {
  private BalanceReconciliationGateway gateway;
  private InternalBalanceSnapshotProvider snapshotProvider;
  private ReconciliationReporter reporter;
  private BalanceReconciliationService service;

  @BeforeEach
  void setUp() {
    gateway = org.mockito.Mockito.mock(BalanceReconciliationGateway.class);
    snapshotProvider = org.mockito.Mockito.mock(InternalBalanceSnapshotProvider.class);
    reporter = org.mockito.Mockito.mock(ReconciliationReporter.class);
    service = new BalanceReconciliationService(gateway, snapshotProvider, reporter);
  }

  @Test
  void shouldReturnNotImplementedWithComputedDrift() {
    when(snapshotProvider.computeInternalTotals())
        .thenReturn(Map.of("USDT", new BigDecimal("120"), "BTC", new BigDecimal("2")));
    when(gateway.fetchExternalBalances())
        .thenReturn(Map.of("USDT", new BigDecimal("100"), "ETH", new BigDecimal("1")));

    ReconciliationResult result = service.runOnce();

    assertEquals(ReconciliationStatus.NOT_IMPLEMENTED, result.status());
    assertEquals(new BigDecimal("20"), result.driftByAsset().get("USDT"));
    assertEquals(new BigDecimal("2"), result.driftByAsset().get("BTC"));
    assertEquals(new BigDecimal("-1"), result.driftByAsset().get("ETH"));
    verify(reporter).report(result);
  }
}
