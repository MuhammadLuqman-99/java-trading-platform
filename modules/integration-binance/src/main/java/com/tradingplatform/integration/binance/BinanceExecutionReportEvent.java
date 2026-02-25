package com.tradingplatform.integration.binance;

import java.math.BigDecimal;
import java.time.Instant;

public record BinanceExecutionReportEvent(
    String symbol,
    String exchangeOrderId,
    String exchangeClientOrderId,
    String exchangeTradeId,
    String side,
    String executionType,
    String orderStatus,
    BigDecimal cumulativeFilledQty,
    BigDecimal lastExecutedQty,
    BigDecimal lastExecutedPrice,
    BigDecimal orderQty,
    BigDecimal orderPrice,
    BigDecimal feeAmount,
    String feeAsset,
    Instant eventTime,
    Instant tradeTime,
    String rawPayload) {}
