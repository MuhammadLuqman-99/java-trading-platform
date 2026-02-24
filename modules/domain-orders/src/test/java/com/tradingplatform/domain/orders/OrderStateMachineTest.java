package com.tradingplatform.domain.orders;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderStateMachineTest {
  @Test
  void shouldAllowForwardTransitions() {
    assertTrue(OrderStateMachine.canTransition(OrderStatus.NEW, OrderStatus.ACK));
    assertTrue(OrderStateMachine.canTransition(OrderStatus.ACK, OrderStatus.FILLED));
    assertTrue(OrderStateMachine.canTransition(OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED));
    assertTrue(OrderStateMachine.canTransition(OrderStatus.PARTIALLY_FILLED, OrderStatus.CANCELED));
  }

  @Test
  void shouldRejectBackwardOrTerminalTransitions() {
    assertFalse(OrderStateMachine.canTransition(OrderStatus.ACK, OrderStatus.NEW));
    assertFalse(OrderStateMachine.canTransition(OrderStatus.FILLED, OrderStatus.ACK));
    assertFalse(OrderStateMachine.canTransition(OrderStatus.CANCELED, OrderStatus.FILLED));
  }

  @Test
  void shouldThrowForInvalidTransition() {
    assertThrows(
        OrderDomainException.class,
        () -> OrderStateMachine.validateTransition(OrderStatus.FILLED, OrderStatus.CANCELED));
  }

  @Test
  void shouldAcceptTransitionValidationForAllowedPath() {
    assertDoesNotThrow(() -> OrderStateMachine.validateTransition(OrderStatus.NEW, OrderStatus.ACK));
  }
}
