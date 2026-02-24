package com.tradingplatform.tradingapi.orders;

import com.tradingplatform.domain.orders.OrderSide;
import com.tradingplatform.domain.orders.OrderType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateOrderCommand(
    UUID orderId,
    UUID accountId,
    String instrument,
    OrderSide side,
    OrderType type,
    BigDecimal qty,
    BigDecimal price,
    String clientOrderId,
    String correlationId,
    Instant occurredAt) {}
