package com.tradingplatform.tradingapi.orders;

import com.tradingplatform.domain.orders.Order;
import com.tradingplatform.tradingapi.risk.RiskCheckService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Qualifier("riskValidatedOrderCreateUseCase")
public class RiskValidatedOrderCreateUseCase implements OrderCreateUseCase {
  private final OrderCreateUseCase delegate;
  private final RiskCheckService riskCheckService;

  public RiskValidatedOrderCreateUseCase(
      @Qualifier("coreOrderCreateUseCase") OrderCreateUseCase delegate,
      RiskCheckService riskCheckService) {
    this.delegate = delegate;
    this.riskCheckService = riskCheckService;
  }

  @Override
  public Order create(CreateOrderCommand command) {
    riskCheckService.validateOrder(command);
    return delegate.create(command);
  }
}
