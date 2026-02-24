package com.tradingplatform.infra.kafka.topics;

import java.util.List;

public final class TopicNames {
  public static final String ORDERS_SUBMITTED_V1 = "orders.submitted.v1";
  public static final String ORDERS_UPDATED_V1 = "orders.updated.v1";
  public static final String EXECUTIONS_RECORDED_V1 = "executions.recorded.v1";
  public static final String BALANCES_UPDATED_V1 = "balances.updated.v1";

  public static final String ORDERS_SUBMITTED_DLQ_V1 = "orders.submitted.dlq.v1";
  public static final String ORDERS_UPDATED_DLQ_V1 = "orders.updated.dlq.v1";
  public static final String EXECUTIONS_RECORDED_DLQ_V1 = "executions.recorded.dlq.v1";
  public static final String BALANCES_UPDATED_DLQ_V1 = "balances.updated.dlq.v1";

  private TopicNames() {}

  public static List<String> all() {
    return List.of(
        ORDERS_SUBMITTED_V1,
        ORDERS_UPDATED_V1,
        EXECUTIONS_RECORDED_V1,
        BALANCES_UPDATED_V1,
        ORDERS_SUBMITTED_DLQ_V1,
        ORDERS_UPDATED_DLQ_V1,
        EXECUTIONS_RECORDED_DLQ_V1,
        BALANCES_UPDATED_DLQ_V1);
  }
}
