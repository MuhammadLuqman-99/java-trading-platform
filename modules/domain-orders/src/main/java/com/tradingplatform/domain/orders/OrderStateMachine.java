package com.tradingplatform.domain.orders;

import java.util.EnumSet;
import java.util.Map;

public final class OrderStateMachine {
  private static final Map<OrderStatus, EnumSet<OrderStatus>> ALLOWED_TRANSITIONS =
      Map.of(
          OrderStatus.NEW,
              EnumSet.of(
                  OrderStatus.ACK, OrderStatus.REJECTED, OrderStatus.CANCELED, OrderStatus.EXPIRED),
          OrderStatus.ACK,
              EnumSet.of(
                  OrderStatus.PARTIALLY_FILLED,
                  OrderStatus.FILLED,
                  OrderStatus.CANCELED,
                  OrderStatus.EXPIRED,
                  OrderStatus.REJECTED),
          OrderStatus.PARTIALLY_FILLED,
              EnumSet.of(
                  OrderStatus.PARTIALLY_FILLED,
                  OrderStatus.FILLED,
                  OrderStatus.CANCELED,
                  OrderStatus.EXPIRED),
          OrderStatus.FILLED, EnumSet.noneOf(OrderStatus.class),
          OrderStatus.CANCELED, EnumSet.noneOf(OrderStatus.class),
          OrderStatus.REJECTED, EnumSet.noneOf(OrderStatus.class),
          OrderStatus.EXPIRED, EnumSet.noneOf(OrderStatus.class));

  private OrderStateMachine() {}

  public static boolean canTransition(OrderStatus from, OrderStatus to) {
    if (from == null || to == null) {
      return false;
    }
    EnumSet<OrderStatus> allowed = ALLOWED_TRANSITIONS.get(from);
    return allowed != null && allowed.contains(to);
  }

  public static void validateTransition(OrderStatus from, OrderStatus to) {
    if (!canTransition(from, to)) {
      throw new OrderDomainException(
          "Invalid order status transition from " + from + " to " + to);
    }
  }
}
