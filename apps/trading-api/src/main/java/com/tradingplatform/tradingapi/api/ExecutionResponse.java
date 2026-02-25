package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.executions.ExecutionView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExecutionResponse(
    UUID id,
    UUID orderId,
    UUID accountId,
    String symbol,
    String side,
    String tradeId,
    String exchangeName,
    String exchangeOrderId,
    BigDecimal qty,
    BigDecimal price,
    String feeAsset,
    BigDecimal feeAmount,
    Instant executedAt) {
  public static ExecutionResponse from(ExecutionView view) {
    return new ExecutionResponse(
        view.id(),
        view.orderId(),
        view.accountId(),
        view.symbol(),
        view.side(),
        view.tradeId(),
        view.exchangeName(),
        view.exchangeOrderId(),
        view.qty(),
        view.price(),
        view.feeAsset(),
        view.feeAmount(),
        view.executedAt());
  }
}
