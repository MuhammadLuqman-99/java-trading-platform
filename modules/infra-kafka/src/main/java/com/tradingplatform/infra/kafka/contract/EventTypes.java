package com.tradingplatform.infra.kafka.contract;

public final class EventTypes {
  public static final String ORDER_SUBMITTED = "OrderSubmitted";
  public static final String ORDER_UPDATED = "OrderUpdated";
  public static final String EXECUTION_RECORDED = "ExecutionRecorded";
  public static final String BALANCE_UPDATED = "BalanceUpdated";

  private EventTypes() {}
}
