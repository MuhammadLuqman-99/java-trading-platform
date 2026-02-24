package com.tradingplatform.infra.kafka.serde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tradingplatform.infra.kafka.contract.EventEnvelope;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV1;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventEnvelopeJsonCodecTest {
  private final EventEnvelopeJsonCodec codec =
      new EventEnvelopeJsonCodec(EventObjectMapperFactory.create());

  @Test
  void shouldRoundTripEnvelopeAndPayload() {
    OrderSubmittedV1 payload =
        new OrderSubmittedV1(
            "ord-1001",
            "acc-2001",
            "BTCUSDT",
            "BUY",
            "LIMIT",
            new BigDecimal("0.015"),
            new BigDecimal("42000.00"),
            Instant.parse("2026-02-24T12:00:00Z"));
    EventEnvelope<OrderSubmittedV1> source =
        EventEnvelope.of(
            EventTypes.ORDER_SUBMITTED, 1, "trading-api", "ord-1001", "ord-1001", payload);

    String json = codec.encode(source);
    EventEnvelope<OrderSubmittedV1> decoded = codec.decode(json, OrderSubmittedV1.class);

    assertNotNull(decoded.eventId());
    assertEquals(source.eventType(), decoded.eventType());
    assertEquals(source.eventVersion(), decoded.eventVersion());
    assertEquals(source.producer(), decoded.producer());
    assertEquals(source.correlationId(), decoded.correlationId());
    assertEquals(source.key(), decoded.key());
    assertEquals(source.payload(), decoded.payload());
  }
}
