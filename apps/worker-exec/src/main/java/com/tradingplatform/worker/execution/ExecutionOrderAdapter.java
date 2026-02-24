package com.tradingplatform.worker.execution;

public interface ExecutionOrderAdapter {
  void submitOrder(SubmitOrderCommand command);
}
