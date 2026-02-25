package com.tradingplatform.tradingapi.executions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ExecutionReadRepository {
  boolean accountExists(UUID accountId);

  List<ExecutionView> findByAccountId(
      UUID accountId,
      UUID orderId,
      String symbol,
      Instant from,
      Instant to,
      int offset,
      int limit);

  long countByAccountId(UUID accountId, UUID orderId, String symbol, Instant from, Instant to);
}
