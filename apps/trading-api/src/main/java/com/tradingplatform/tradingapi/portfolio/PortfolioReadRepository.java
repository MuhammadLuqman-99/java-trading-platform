package com.tradingplatform.tradingapi.portfolio;

import java.util.List;
import java.util.UUID;

public interface PortfolioReadRepository {
  boolean accountExists(UUID accountId);

  List<WalletBalanceView> findBalancesByAccountId(UUID accountId);

  List<PositionView> findPositionsByAccountId(UUID accountId);
}
