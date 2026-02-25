package com.tradingplatform.tradingapi.executions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionQueryService {
  private final ExecutionReadRepository executionReadRepository;

  public ExecutionQueryService(ExecutionReadRepository executionReadRepository) {
    this.executionReadRepository = executionReadRepository;
  }

  @Transactional(readOnly = true)
  public ExecutionPage listExecutions(
      UUID accountId, UUID orderId, String symbol, Instant from, Instant to, int page, int size) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("from must be before or equal to to");
    }
    if (!executionReadRepository.accountExists(accountId)) {
      throw new IllegalArgumentException("Account not found: " + accountId);
    }

    int safePage = Math.max(page, 0);
    int clampedSize = Math.min(Math.max(size, 1), 100);
    int offset = safePage * clampedSize;
    String normalizedSymbol = normalizeSymbol(symbol);

    List<ExecutionView> executions =
        executionReadRepository.findByAccountId(
            accountId, orderId, normalizedSymbol, from, to, offset, clampedSize);
    long totalElements =
        executionReadRepository.countByAccountId(accountId, orderId, normalizedSymbol, from, to);
    int totalPages = (int) Math.ceil((double) totalElements / clampedSize);
    return new ExecutionPage(executions, safePage, clampedSize, totalElements, totalPages);
  }

  private static String normalizeSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      return null;
    }
    return symbol.trim().toUpperCase();
  }

  public record ExecutionPage(
      List<ExecutionView> executions, int page, int size, long totalElements, int totalPages) {}
}
