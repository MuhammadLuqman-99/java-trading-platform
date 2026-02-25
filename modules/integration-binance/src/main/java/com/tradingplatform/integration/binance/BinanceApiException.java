package com.tradingplatform.integration.binance;

import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpHeaders;

public class BinanceApiException extends RuntimeException {
  private final int statusCode;
  private final HttpHeaders responseHeaders;
  private final String responseBody;
  private final Integer binanceErrorCode;

  public BinanceApiException(
      int statusCode, HttpHeaders responseHeaders, String responseBody, Integer binanceErrorCode) {
    super("Binance API error status=" + statusCode + ", binanceCode=" + binanceErrorCode);
    this.statusCode = statusCode;
    this.responseHeaders =
        HttpHeaders.readOnlyHttpHeaders(
            responseHeaders == null ? HttpHeaders.EMPTY : responseHeaders);
    this.responseBody = Objects.requireNonNullElse(responseBody, "");
    this.binanceErrorCode = binanceErrorCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public HttpHeaders responseHeaders() {
    return responseHeaders;
  }

  public String responseBody() {
    return responseBody;
  }

  public Integer binanceErrorCode() {
    return binanceErrorCode;
  }

  public Optional<String> retryAfterHeader() {
    return Optional.ofNullable(responseHeaders.getFirst(HttpHeaders.RETRY_AFTER));
  }

  public boolean isRateLimitError() {
    return statusCode == 429 || statusCode == 418 || Integer.valueOf(-1003).equals(binanceErrorCode);
  }
}
