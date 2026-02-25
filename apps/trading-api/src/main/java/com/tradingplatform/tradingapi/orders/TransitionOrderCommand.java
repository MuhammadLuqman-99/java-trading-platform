package com.tradingplatform.tradingapi.orders;

import com.tradingplatform.domain.orders.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransitionOrderCommand(
    UUID orderId,
    OrderStatus toStatus,
    BigDecimal filledQty,
    String exchangeName,
    String exchangeOrderId,
    String exchangeClientOrderId,
    String reason,
    String correlationId,
    Instant occurredAt) {}
