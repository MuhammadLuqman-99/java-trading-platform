package com.tradingplatform.integration.binance;

import com.tradingplatform.domain.orders.OrderStatus;
import java.math.BigDecimal;

public record BinanceCancelOrderResult(
    String symbol,
    String exchangeOrderId,
    String clientOrderId,
    String externalStatus,
    OrderStatus domainStatus,
    BigDecimal executedQty,
    String rawPayload) {}
