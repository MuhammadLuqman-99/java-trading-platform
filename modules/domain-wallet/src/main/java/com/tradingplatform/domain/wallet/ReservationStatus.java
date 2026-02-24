package com.tradingplatform.domain.wallet;

public enum ReservationStatus {
  ACTIVE,
  RELEASED,
  CONSUMED,
  CANCELLED;

  public boolean isTerminal() {
    return this == RELEASED || this == CONSUMED || this == CANCELLED;
  }
}
