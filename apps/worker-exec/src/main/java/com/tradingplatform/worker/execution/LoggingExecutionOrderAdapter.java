package com.tradingplatform.worker.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingExecutionOrderAdapter implements ExecutionOrderAdapter {
  private static final Logger log = LoggerFactory.getLogger(LoggingExecutionOrderAdapter.class);

  @Override
  public ExecutionAckResult placeOrder(SubmitOrderCommand command) {
    String exchangeName = "BINANCE";
    String exchangeOrderId = "binance-" + command.orderId();
    String exchangeClientOrderId = command.orderId();
    log.info(
        "Execution adapter stub accepted orderId={} accountId={} instrument={} side={} qty={} exchange={} exchangeOrderId={} exchangeClientOrderId={}",
        command.orderId(),
        command.accountId(),
        command.instrument(),
        command.side(),
        command.qty(),
        exchangeName,
        exchangeOrderId,
        exchangeClientOrderId);
    return new ExecutionAckResult(exchangeName, exchangeOrderId, exchangeClientOrderId);
  }
}
