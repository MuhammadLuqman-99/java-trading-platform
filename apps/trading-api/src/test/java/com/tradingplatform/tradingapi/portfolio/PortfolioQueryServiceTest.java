package com.tradingplatform.tradingapi.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.tradingplatform.tradingapi.api.BalancesResponse;
import com.tradingplatform.tradingapi.api.PortfolioResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortfolioQueryServiceTest {
  private PortfolioReadRepository portfolioReadRepository;
  private PortfolioQueryService service;

  @BeforeEach
  void setUp() {
    portfolioReadRepository = org.mockito.Mockito.mock(PortfolioReadRepository.class);
    service = new PortfolioQueryService(portfolioReadRepository);
  }

  @Test
  void shouldMapBalancesWithTotal() {
    UUID accountId = UUID.randomUUID();
    when(portfolioReadRepository.accountExists(accountId)).thenReturn(true);
    when(portfolioReadRepository.findBalancesByAccountId(accountId))
        .thenReturn(
            List.of(
                new WalletBalanceView(
                    accountId,
                    "USDT",
                    new BigDecimal("100.50"),
                    new BigDecimal("25.00"),
                    Instant.parse("2026-02-24T12:00:00Z"))));

    BalancesResponse response = service.getBalances(accountId);

    assertEquals(accountId, response.accountId());
    assertEquals(1, response.balances().size());
    assertEquals(new BigDecimal("125.50"), response.balances().get(0).total());
  }

  @Test
  void shouldMapPortfolioWithDerivedPositions() {
    UUID accountId = UUID.randomUUID();
    when(portfolioReadRepository.accountExists(accountId)).thenReturn(true);
    when(portfolioReadRepository.findBalancesByAccountId(accountId)).thenReturn(List.of());
    when(portfolioReadRepository.findPositionsByAccountId(accountId))
        .thenReturn(
            List.of(
                new PositionView("BTCUSDT", new BigDecimal("0.25")),
                new PositionView("ETHUSDT", new BigDecimal("-1.5"))));

    PortfolioResponse response = service.getPortfolio(accountId);

    assertEquals(accountId, response.accountId());
    assertEquals(2, response.positions().size());
    assertEquals("BTCUSDT", response.positions().get(0).symbol());
    assertEquals(new BigDecimal("-1.5"), response.positions().get(1).netQty());
  }

  @Test
  void shouldRejectUnknownAccount() {
    UUID accountId = UUID.randomUUID();
    when(portfolioReadRepository.accountExists(accountId)).thenReturn(false);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> service.getBalances(accountId));
    assertEquals("Account not found: " + accountId, ex.getMessage());
  }
}
