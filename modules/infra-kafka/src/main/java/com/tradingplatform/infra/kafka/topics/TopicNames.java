package com.tradingplatform.infra.kafka.topics;

import java.util.List;

public final class TopicNames {
  public static final String ORDERS_SUBMITTED_V1 = "orders.submitted.v1";
  public static final String ORDERS_SUBMITTED_V2 = "orders.submitted.v2";
  public static final String ORDERS_UPDATED_V1 = "orders.updated.v1";
  public static final String ORDERS_UPDATED_V2 = "orders.updated.v2";
  public static final String ORDERS_UPDATED_V3 = "orders.updated.v3";
  public static final String EXECUTIONS_RECORDED_V1 = "executions.recorded.v1";
  public static final String EXECUTIONS_RECORDED_V2 = "executions.recorded.v2";
  public static final String BALANCES_UPDATED_V1 = "balances.updated.v1";

  public static final String ORDERS_SUBMITTED_DLQ_V1 = "orders.submitted.dlq.v1";
  public static final String ORDERS_SUBMITTED_DLQ_V2 = "orders.submitted.dlq.v2";
  public static final String ORDERS_UPDATED_DLQ_V1 = "orders.updated.dlq.v1";
  public static final String ORDERS_UPDATED_DLQ_V2 = "orders.updated.dlq.v2";
  public static final String ORDERS_UPDATED_DLQ_V3 = "orders.updated.dlq.v3";
  public static final String EXECUTIONS_RECORDED_DLQ_V1 = "executions.recorded.dlq.v1";
  public static final String EXECUTIONS_RECORDED_DLQ_V2 = "executions.recorded.dlq.v2";
  public static final String BALANCES_UPDATED_DLQ_V1 = "balances.updated.dlq.v1";

  private TopicNames() {}

  public static List<String> all() {
    return List.of(
        ORDERS_SUBMITTED_V1,
        ORDERS_SUBMITTED_V2,
        ORDERS_UPDATED_V1,
        ORDERS_UPDATED_V2,
        ORDERS_UPDATED_V3,
        EXECUTIONS_RECORDED_V1,
        EXECUTIONS_RECORDED_V2,
        BALANCES_UPDATED_V1,
        ORDERS_SUBMITTED_DLQ_V1,
        ORDERS_SUBMITTED_DLQ_V2,
        ORDERS_UPDATED_DLQ_V1,
        ORDERS_UPDATED_DLQ_V2,
        ORDERS_UPDATED_DLQ_V3,
        EXECUTIONS_RECORDED_DLQ_V1,
        EXECUTIONS_RECORDED_DLQ_V2,
        BALANCES_UPDATED_DLQ_V1);
  }
}
