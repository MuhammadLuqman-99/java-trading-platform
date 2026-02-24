package com.tradingplatform.domain.orders;

public enum OrderStatus {
  NEW,
  ACK,
  PARTIALLY_FILLED,
  FILLED,
  CANCELED,
  REJECTED,
  EXPIRED;

  public boolean isTerminal() {
    return this == FILLED || this == CANCELED || this == REJECTED || this == EXPIRED;
  }
}
