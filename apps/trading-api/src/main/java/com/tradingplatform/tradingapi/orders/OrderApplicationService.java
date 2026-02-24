package com.tradingplatform.tradingapi.orders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.orders.Order;
import com.tradingplatform.domain.orders.OrderDomainException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderApplicationService {
  private final OrderRepository orderRepository;
  private final OrderEventRepository orderEventRepository;
  private final OutboxAppendRepository outboxAppendRepository;
  private final ObjectMapper objectMapper;

  public OrderApplicationService(
      OrderRepository orderRepository,
      OrderEventRepository orderEventRepository,
      OutboxAppendRepository outboxAppendRepository,
      ObjectMapper objectMapper) {
    this.orderRepository = orderRepository;
    this.orderEventRepository = orderEventRepository;
    this.outboxAppendRepository = outboxAppendRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Order createOrder(CreateOrderCommand command) {
    Instant occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
    UUID orderId = command.orderId() == null ? UUID.randomUUID() : command.orderId();
    Order order =
        Order.createNew(
            orderId,
            command.accountId(),
            command.instrument(),
            command.side(),
            command.type(),
            command.qty(),
            command.price(),
            command.clientOrderId(),
            occurredAt);
    orderRepository.insert(order);
    orderEventRepository.append(
        new OrderEventAppend(
            order.id(), "ORDER_CREATED", null, order.status(), toJson(createPayload(command, occurredAt))));
    outboxAppendRepository.appendOrderSubmitted(order, command.correlationId(), occurredAt);
    return order;
  }

  @Transactional
  public Order transitionOrder(TransitionOrderCommand command) {
    Order current =
        orderRepository
            .findById(command.orderId())
            .orElseThrow(() -> new OrderDomainException("Order not found: " + command.orderId()));
    Instant occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
    Order next =
        current.transitionTo(
            command.toStatus(),
            command.filledQty(),
            command.exchangeOrderId() == null ? current.exchangeOrderId() : command.exchangeOrderId(),
            occurredAt);
    orderRepository.update(next);
    orderEventRepository.append(
        new OrderEventAppend(
            next.id(),
            "ORDER_STATUS_CHANGED",
            current.status(),
            next.status(),
            toJson(transitionPayload(command, next, occurredAt))));
    outboxAppendRepository.appendOrderUpdated(next, current.status(), command.correlationId(), occurredAt);
    return next;
  }

  private Map<String, Object> createPayload(CreateOrderCommand command, Instant occurredAt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("accountId", command.accountId());
    payload.put("instrument", command.instrument());
    payload.put("side", command.side());
    payload.put("type", command.type());
    payload.put("qty", command.qty());
    payload.put("price", command.price());
    payload.put("clientOrderId", command.clientOrderId());
    payload.put("occurredAt", occurredAt);
    return payload;
  }

  private Map<String, Object> transitionPayload(
      TransitionOrderCommand command, Order next, Instant occurredAt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("reason", command.reason());
    payload.put("filledQty", next.filledQty());
    payload.put("remainingQty", next.qty().subtract(next.filledQty()));
    payload.put("exchangeOrderId", next.exchangeOrderId());
    payload.put("occurredAt", occurredAt);
    return payload;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize order event payload", ex);
    }
  }
}
