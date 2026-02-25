package com.tradingplatform.tradingapi.instruments;

public class InstrumentNotFoundException extends RuntimeException {
  public InstrumentNotFoundException(String symbol) {
    super("Instrument not found: " + symbol);
  }
}
