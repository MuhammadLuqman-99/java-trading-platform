package com.tradingplatform.domain.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {
  @Test
  void shouldCreateNewLimitOrderAndTransition() {
    Instant now = Instant.parse("2026-02-24T00:00:00Z");
    Order order =
        Order.createNew(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BTCUSDT",
            OrderSide.BUY,
            OrderType.LIMIT,
            new BigDecimal("0.5"),
            new BigDecimal("45000"),
            "client-1",
            now);

    Order transitioned =
        order.transitionTo(
            OrderStatus.ACK, new BigDecimal("0.1"), "exchange-1", now.plusSeconds(1));

    assertEquals(OrderStatus.ACK, transitioned.status());
    assertEquals(new BigDecimal("0.1"), transitioned.filledQty());
    assertEquals("exchange-1", transitioned.exchangeOrderId());
  }

  @Test
  void shouldRejectMarketOrderWithPrice() {
    Instant now = Instant.parse("2026-02-24T00:00:00Z");
    assertThrows(
        OrderDomainException.class,
        () ->
            Order.createNew(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ETHUSDT",
                OrderSide.SELL,
                OrderType.MARKET,
                new BigDecimal("1"),
                new BigDecimal("2000"),
                "client-2",
                now));
  }
}
