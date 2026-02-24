package com.tradingplatform.infra.kafka.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MicrometerKafkaTelemetryTest {
  @Test
  void shouldRecordPublishAndConsumeMetrics() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    MicrometerKafkaTelemetry telemetry = new MicrometerKafkaTelemetry(registry);

    telemetry.onPublishSuccess("orders.submitted.v1", "ord-1001", "OrderSubmitted", 5_000_000L);
    telemetry.onPublishFailure(
        "orders.submitted.v1", "ord-1001", "OrderSubmitted", new IllegalStateException("boom"));
    telemetry.onConsumeSuccess(
        "orders.submitted.v1", "ord-1001", "OrderSubmitted", 0, 12L, 8_000_000L);
    telemetry.onConsumeFailure(
        "orders.submitted.v1", "ord-1001", "OrderSubmitted", new RuntimeException("bad"));
    telemetry.onDeadLetter("orders.submitted.v1", "ord-1001", new RuntimeException("dlq"));

    assertEquals(
        1.0d,
        registry
            .get("infra.kafka.publish.total")
            .tag("topic", "orders.submitted.v1")
            .tag("event_type", "OrderSubmitted")
            .tag("outcome", "success")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .get("infra.kafka.publish.total")
            .tag("topic", "orders.submitted.v1")
            .tag("event_type", "OrderSubmitted")
            .tag("outcome", "failure")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .get("infra.kafka.consume.total")
            .tag("topic", "orders.submitted.v1")
            .tag("event_type", "OrderSubmitted")
            .tag("outcome", "success")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .get("infra.kafka.consume.total")
            .tag("topic", "orders.submitted.v1")
            .tag("event_type", "OrderSubmitted")
            .tag("outcome", "failure")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .get("infra.kafka.deadletter.total")
            .tag("topic", "orders.submitted.v1")
            .counter()
            .count());

    Timer publishTimer =
        registry
            .get("infra.kafka.publish.duration")
            .tag("topic", "orders.submitted.v1")
            .tag("event_type", "OrderSubmitted")
            .timer();
    Timer consumeTimer =
        registry
            .get("infra.kafka.consume.duration")
            .tag("topic", "orders.submitted.v1")
            .tag("event_type", "OrderSubmitted")
            .timer();

    assertNotNull(publishTimer);
    assertNotNull(consumeTimer);
    assertEquals(1L, publishTimer.count());
    assertEquals(1L, consumeTimer.count());
  }
}
