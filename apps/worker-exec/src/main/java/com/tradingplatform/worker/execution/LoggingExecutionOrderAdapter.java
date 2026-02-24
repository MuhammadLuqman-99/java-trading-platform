package com.tradingplatform.worker.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class LoggingExecutionOrderAdapter implements ExecutionOrderAdapter {
  private static final Logger log = LoggerFactory.getLogger(LoggingExecutionOrderAdapter.class);

  @Override
  public void submitOrder(SubmitOrderCommand command) {
    log.info(
        "Execution adapter stub accepted orderId={} accountId={} instrument={} side={} qty={}",
        command.orderId(),
        command.accountId(),
        command.instrument(),
        command.side(),
        command.qty());
  }
}
