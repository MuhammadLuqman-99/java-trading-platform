package com.tradingplatform.worker.execution;

import com.tradingplatform.integration.binance.BinanceOrderClient;
import com.tradingplatform.integration.binance.BinanceOrderSubmitRequest;
import com.tradingplatform.integration.binance.BinanceOrderSubmitResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BinanceExecutionOrderAdapter implements ExecutionOrderAdapter {
  private static final Logger log = LoggerFactory.getLogger(BinanceExecutionOrderAdapter.class);
  private static final String LIMIT = "LIMIT";

  private final BinanceOrderClient binanceOrderClient;

  public BinanceExecutionOrderAdapter(BinanceOrderClient binanceOrderClient) {
    this.binanceOrderClient = binanceOrderClient;
  }

  @Override
  public ExecutionAckResult placeOrder(SubmitOrderCommand command) {
    BinanceOrderSubmitRequest request =
        new BinanceOrderSubmitRequest(
            command.instrument(),
            command.side(),
            command.type(),
            command.qty(),
            LIMIT.equalsIgnoreCase(command.type()) ? command.price() : null,
            command.orderId());
    BinanceOrderSubmitResponse response = binanceOrderClient.submitOrder(request);
    log.info(
        "Submitted orderId={} to Binance exchangeOrderId={} status={} clientOrderId={}",
        command.orderId(),
        response.exchangeOrderId(),
        response.status(),
        response.clientOrderId());
    return new ExecutionAckResult("BINANCE", response.exchangeOrderId(), response.clientOrderId());
  }
}
