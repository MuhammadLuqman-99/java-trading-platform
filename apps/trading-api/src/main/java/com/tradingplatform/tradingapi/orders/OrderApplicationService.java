package com.tradingplatform.tradingapi.orders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.orders.Order;
import com.tradingplatform.domain.orders.OrderDomainException;
import com.tradingplatform.domain.orders.OrderStatus;
import com.tradingplatform.tradingapi.wallet.WalletReservationService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderApplicationService {
  private final OrderRepository orderRepository;
  private final OrderEventRepository orderEventRepository;
  private final OutboxAppendRepository outboxAppendRepository;
  private final WalletReservationService walletReservationService;
  private final ObjectMapper objectMapper;

  public OrderApplicationService(
      OrderRepository orderRepository,
      OrderEventRepository orderEventRepository,
      OutboxAppendRepository outboxAppendRepository,
      WalletReservationService walletReservationService,
      ObjectMapper objectMapper) {
    this.orderRepository = orderRepository;
    this.orderEventRepository = orderEventRepository;
    this.outboxAppendRepository = outboxAppendRepository;
    this.walletReservationService = walletReservationService;
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

  @Transactional
  public Order cancelOrder(CancelOrderCommand command) {
    Order current =
        orderRepository
            .findById(command.orderId())
            .orElseThrow(
                () -> new OrderDomainException("Order not found: " + command.orderId()));

    if (!current.accountId().equals(command.accountId())) {
      throw new OrderDomainException(
          "Order does not belong to account " + command.accountId());
    }

    Instant occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
    Order canceled = current.transitionTo(OrderStatus.CANCELED, null, null, occurredAt);

    orderRepository.update(canceled);
    orderEventRepository.append(
        new OrderEventAppend(
            canceled.id(),
            "ORDER_CANCELED",
            current.status(),
            canceled.status(),
            toJson(cancelPayload(command, canceled, occurredAt))));
    outboxAppendRepository.appendOrderUpdated(
        canceled, current.status(), command.correlationId(), occurredAt);

    walletReservationService.release(command.orderId());

    return canceled;
  }

  @Transactional(readOnly = true)
  public Order findById(UUID orderId) {
    return orderRepository
        .findById(orderId)
        .orElseThrow(() -> new OrderDomainException("Order not found: " + orderId));
  }

  @Transactional(readOnly = true)
  public List<Order> findByAccountId(
      UUID accountId, String status, String instrument, int offset, int limit) {
    return orderRepository.findByAccountId(accountId, status, instrument, offset, limit);
  }

  @Transactional(readOnly = true)
  public long countByAccountId(UUID accountId, String status, String instrument) {
    return orderRepository.countByAccountId(accountId, status, instrument);
  }

  private Map<String, Object> cancelPayload(
      CancelOrderCommand command, Order canceled, Instant occurredAt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("reason", command.reason());
    payload.put("accountId", canceled.accountId());
    payload.put("instrument", canceled.instrument());
    payload.put("occurredAt", occurredAt);
    return payload;
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
