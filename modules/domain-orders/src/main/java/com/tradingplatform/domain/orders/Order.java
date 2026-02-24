package com.tradingplatform.domain.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Order(
    UUID id,
    UUID accountId,
    String instrument,
    OrderSide side,
    OrderType type,
    BigDecimal qty,
    BigDecimal price,
    OrderStatus status,
    BigDecimal filledQty,
    String clientOrderId,
    String exchangeOrderId,
    Instant createdAt,
    Instant updatedAt) {
  public Order {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(accountId, "accountId must not be null");
    requireNonBlank(instrument, "instrument");
    Objects.requireNonNull(side, "side must not be null");
    Objects.requireNonNull(type, "type must not be null");
    requirePositive(qty, "qty");
    validatePriceByType(type, price);
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(filledQty, "filledQty must not be null");
    if (filledQty.compareTo(BigDecimal.ZERO) < 0 || filledQty.compareTo(qty) > 0) {
      throw new OrderDomainException("filledQty must be between 0 and qty");
    }
    requireNonBlank(clientOrderId, "clientOrderId");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  public static Order createNew(
      UUID id,
      UUID accountId,
      String instrument,
      OrderSide side,
      OrderType type,
      BigDecimal qty,
      BigDecimal price,
      String clientOrderId,
      Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    return new Order(
        id,
        accountId,
        instrument,
        side,
        type,
        qty,
        price,
        OrderStatus.NEW,
        BigDecimal.ZERO,
        clientOrderId,
        null,
        now,
        now);
  }

  public Order transitionTo(
      OrderStatus toStatus, BigDecimal nextFilledQty, String nextExchangeOrderId, Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    OrderStateMachine.validateTransition(status, toStatus);
    BigDecimal safeFilledQty = nextFilledQty == null ? filledQty : nextFilledQty;
    if (safeFilledQty.compareTo(BigDecimal.ZERO) < 0 || safeFilledQty.compareTo(qty) > 0) {
      throw new OrderDomainException("filledQty must be between 0 and qty");
    }
    return new Order(
        id,
        accountId,
        instrument,
        side,
        type,
        qty,
        price,
        toStatus,
        safeFilledQty,
        clientOrderId,
        nextExchangeOrderId,
        createdAt,
        now);
  }

  private static void validatePriceByType(OrderType type, BigDecimal price) {
    if (type == OrderType.MARKET) {
      if (price != null) {
        throw new OrderDomainException("Market order price must be null");
      }
      return;
    }
    requirePositive(price, "price");
  }

  private static void requirePositive(BigDecimal value, String fieldName) {
    if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new OrderDomainException(fieldName + " must be > 0");
    }
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new OrderDomainException(fieldName + " must not be blank");
    }
  }
}
