package com.tradingplatform.tradingapi.api;

import com.tradingplatform.domain.orders.Order;
import com.tradingplatform.tradingapi.orders.CancelOrderCommand;
import com.tradingplatform.tradingapi.orders.CreateOrderCommand;
import com.tradingplatform.tradingapi.orders.OrderApplicationService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  public ResponseEntity<CreateOrderResponse> createOrder(
      @Valid @RequestBody CreateOrderRequest request) {
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

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasRole('TRADER')")
  public ResponseEntity<CancelOrderResponse> cancelOrder(
      @PathVariable("id") UUID id, @Valid @RequestBody CancelOrderRequest request) {
    Instant now = Instant.now();
    String reason = request.reason() != null ? request.reason() : "user_requested";

    Order canceled =
        orderApplicationService.cancelOrder(
            new CancelOrderCommand(
                id, request.accountId(), reason, UUID.randomUUID().toString(), now));

    return ResponseEntity.ok(new CancelOrderResponse(canceled.id(), canceled.status().name()));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('TRADER')")
  public ResponseEntity<OrderResponse> getOrder(@PathVariable("id") UUID id) {
    Order order = orderApplicationService.findById(id);
    return ResponseEntity.ok(OrderResponse.from(order));
  }

  @GetMapping
  @PreAuthorize("hasRole('TRADER')")
  public ResponseEntity<OrdersPageResponse> listOrders(
      @RequestParam("accountId") UUID accountId,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "symbol", required = false) String symbol,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    int clampedSize = Math.min(Math.max(size, 1), 100);
    int offset = page * clampedSize;

    List<Order> orders =
        orderApplicationService.findByAccountId(accountId, status, symbol, offset, clampedSize);
    long totalElements = orderApplicationService.countByAccountId(accountId, status, symbol);
    int totalPages = (int) Math.ceil((double) totalElements / clampedSize);

    List<OrderResponse> orderResponses = orders.stream().map(OrderResponse::from).toList();
    return ResponseEntity.ok(
        new OrdersPageResponse(orderResponses, page, clampedSize, totalElements, totalPages));
  }
}
