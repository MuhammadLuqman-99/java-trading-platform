package com.tradingplatform.tradingapi.orders;

import com.tradingplatform.domain.orders.Order;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Qualifier("coreOrderCreateUseCase")
public class CoreOrderCreateUseCase implements OrderCreateUseCase {
  private final OrderApplicationService orderApplicationService;

  public CoreOrderCreateUseCase(OrderApplicationService orderApplicationService) {
    this.orderApplicationService = orderApplicationService;
  }

  @Override
  public Order create(CreateOrderCommand command) {
    return orderApplicationService.createOrder(command);
  }
}
