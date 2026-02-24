package com.tradingplatform.tradingapi.portfolio;

import com.tradingplatform.tradingapi.api.BalanceItemResponse;
import com.tradingplatform.tradingapi.api.BalancesResponse;
import com.tradingplatform.tradingapi.api.PortfolioResponse;
import com.tradingplatform.tradingapi.api.PositionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioQueryService {
  private final PortfolioReadRepository portfolioReadRepository;

  public PortfolioQueryService(PortfolioReadRepository portfolioReadRepository) {
    this.portfolioReadRepository = portfolioReadRepository;
  }

  @Transactional(readOnly = true)
  public BalancesResponse getBalances(UUID accountId) {
    ensureAccountExists(accountId);
    List<BalanceItemResponse> balances =
        portfolioReadRepository.findBalancesByAccountId(accountId).stream()
            .map(this::toBalanceItem)
            .toList();
    return new BalancesResponse(accountId, balances);
  }

  @Transactional(readOnly = true)
  public PortfolioResponse getPortfolio(UUID accountId) {
    BalancesResponse balances = getBalances(accountId);
    List<PositionResponse> positions =
        portfolioReadRepository.findPositionsByAccountId(accountId).stream()
            .map(position -> new PositionResponse(position.symbol(), position.netQty()))
            .toList();
    return new PortfolioResponse(accountId, balances.balances(), positions, Instant.now());
  }

  private void ensureAccountExists(UUID accountId) {
    if (!portfolioReadRepository.accountExists(accountId)) {
      throw new IllegalArgumentException("Account not found: " + accountId);
    }
  }

  private BalanceItemResponse toBalanceItem(WalletBalanceView view) {
    BigDecimal total = view.available().add(view.reserved());
    return new BalanceItemResponse(
        view.asset(), view.available(), view.reserved(), total, view.updatedAt());
  }
}
