package com.tradingplatform.tradingapi.orders;

import com.tradingplatform.domain.orders.OrderStatus;
import java.util.UUID;

public record OrderEventAppend(
    UUID orderId,
    String eventType,
    OrderStatus fromStatus,
    OrderStatus toStatus,
    String payloadJson) {}
