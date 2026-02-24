package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.orders.CreateOrderCommand;
import com.tradingplatform.tradingapi.orders.OrderApplicationService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/orders")
public class OrderController {
  private final OrderApplicationService orderApplicationService;

  public OrderController(OrderApplicationService orderApplicationService) {
    this.orderApplicationService = orderApplicationService;
  }

  @PostMapping
  @PreAuthorize("hasRole('TRADER')")
  public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
    UUID orderId = UUID.randomUUID();
    Instant now = Instant.now();
    String clientOrderId =
        request.clientOrderId() == null || request.clientOrderId().isBlank()
            ? orderId.toString()
            : request.clientOrderId();

    orderApplicationService.createOrder(
        new CreateOrderCommand(
            orderId,
            request.accountId(),
            request.symbol(),
            request.side(),
            request.type(),
            request.qty(),
            request.price(),
            clientOrderId,
            orderId.toString(),
            now));

    return ResponseEntity.accepted().body(new CreateOrderResponse(orderId));
  }
}
