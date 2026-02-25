package com.tradingplatform.worker.execution;

public record ExecutionAckResult(
    String exchangeName, String exchangeOrderId, String exchangeClientOrderId) {}
