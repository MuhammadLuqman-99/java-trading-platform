package com.tradingplatform.worker.execution;

public interface ExecutionOrderAdapter {
  ExecutionAckResult placeOrder(SubmitOrderCommand command);
}
