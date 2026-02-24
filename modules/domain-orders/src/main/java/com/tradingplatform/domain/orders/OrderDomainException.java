package com.tradingplatform.domain.orders;

public class OrderDomainException extends RuntimeException {
  public OrderDomainException(String message) {
    super(message);
  }
}
