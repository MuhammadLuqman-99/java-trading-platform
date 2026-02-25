package com.tradingplatform.integration.binance;

import com.tradingplatform.domain.orders.OrderStatus;
import java.math.BigDecimal;

public record BinanceQueryOrderResult(
    String symbol,
    String exchangeOrderId,
    String clientOrderId,
    String externalStatus,
    OrderStatus domainStatus,
    BigDecimal originalQty,
    BigDecimal executedQty,
    String rawPayload) {}
