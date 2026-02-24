package com.tradingplatform.infra.kafka.contract;

public final class EventHeaders {
  public static final String X_EVENT_TYPE = "x-event-type";
  public static final String X_EVENT_VERSION = "x-event-version";
  public static final String X_CORRELATION_ID = "x-correlation-id";
  public static final String CONTENT_TYPE = "content-type";
  public static final String APPLICATION_JSON = "application/json";

  private EventHeaders() {}
}
