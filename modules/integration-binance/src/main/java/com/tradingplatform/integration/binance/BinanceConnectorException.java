package com.tradingplatform.integration.binance;

public class BinanceConnectorException extends RuntimeException {
  private final Integer binanceCode;
  private final int httpStatus;

  public BinanceConnectorException(
      String message, int httpStatus, Integer binanceCode, Throwable cause) {
    super(message, cause);
    this.httpStatus = httpStatus;
    this.binanceCode = binanceCode;
  }

  public BinanceConnectorException(String message, int httpStatus, Integer binanceCode) {
    super(message);
    this.httpStatus = httpStatus;
    this.binanceCode = binanceCode;
  }

  public Integer binanceCode() {
    return binanceCode;
  }

  public int httpStatus() {
    return httpStatus;
  }
}
