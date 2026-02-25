package com.tradingplatform.worker.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.tradingplatform.integration.binance.BinanceConnectorException;
import com.tradingplatform.integration.binance.BinanceOpenOrderSnapshot;
import com.tradingplatform.integration.binance.BinancePollingClient;
import com.tradingplatform.integration.binance.BinanceTradeSnapshot;
import com.tradingplatform.worker.execution.BinanceConnectorProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BinanceCatchUpPollerTest {
  @Test
  void shouldMarkConnectorUpWhenCatchUpSucceeds() {
    BinancePollingClient pollingClient = org.mockito.Mockito.mock(BinancePollingClient.class);
    when(pollingClient.fetchOpenOrders())
        .thenReturn(
            List.of(
                new BinanceOpenOrderSnapshot("BTCUSDT", "ord-1", "1001", "NEW"),
                new BinanceOpenOrderSnapshot("ETHUSDT", "ord-2", "1002", "NEW")));
    when(pollingClient.fetchRecentTrades("BTCUSDT", Instant.parse("2026-02-25T11:30:00Z")))
        .thenReturn(List.of(new BinanceTradeSnapshot("BTCUSDT", "501", "1001", "BUY", Instant.now())));
    when(pollingClient.fetchRecentTrades("ETHUSDT", Instant.parse("2026-02-25T11:30:00Z")))
        .thenReturn(
            List.of(
                new BinanceTradeSnapshot("ETHUSDT", "601", "1002", "SELL", Instant.now()),
                new BinanceTradeSnapshot("ETHUSDT", "602", "1003", "BUY", Instant.now())));

    InMemoryConnectorHealthRepository healthRepository = new InMemoryConnectorHealthRepository();
    ActiveInstrumentRepository instrumentRepository = () -> List.of("BTCUSDT", "ETHUSDT");
    BinanceConnectorProperties properties = new BinanceConnectorProperties();
    properties.getCatchup().setRecentTradesLookbackMinutes(30);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:00:00Z"), ZoneOffset.UTC);

    BinanceCatchUpPoller poller =
        new BinanceCatchUpPoller(
            pollingClient, instrumentRepository, healthRepository, properties, meterRegistry, clock);

    poller.runCatchUp();

    ConnectorHealthState state = healthRepository.current;
    assertNotNull(state);
    assertEquals("binance-spot", state.connectorName());
    assertEquals(ConnectorHealthStatus.UP, state.status());
    assertEquals(2, state.openOrdersFetched());
    assertEquals(3, state.recentTradesFetched());
    assertNull(state.lastErrorCode());
    assertNull(state.lastErrorMessage());
    assertEquals(1.0, meterRegistry.counter("worker.connector.poll.total", "connector", "binance-spot", "operation", "open_orders", "outcome", "success").count());
  }

  @Test
  void shouldMarkConnectorDegradedOnFailureAfterRecentSuccess() {
    BinancePollingClient pollingClient = org.mockito.Mockito.mock(BinancePollingClient.class);
    when(pollingClient.fetchOpenOrders())
        .thenThrow(new BinanceConnectorException("rate limit", 429, -1003));

    InMemoryConnectorHealthRepository healthRepository = new InMemoryConnectorHealthRepository();
    healthRepository.current =
        new ConnectorHealthState(
            "binance-spot",
            ConnectorHealthStatus.UP,
            Instant.parse("2026-02-25T11:59:00Z"),
            null,
            null,
            null,
            null,
            null,
            4,
            7,
            Instant.parse("2026-02-25T11:59:00Z"));
    ActiveInstrumentRepository instrumentRepository = List::of;
    BinanceConnectorProperties properties = new BinanceConnectorProperties();
    properties.getHealth().setDownThresholdMinutes(5);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:00:00Z"), ZoneOffset.UTC);

    BinanceCatchUpPoller poller =
        new BinanceCatchUpPoller(
            pollingClient, instrumentRepository, healthRepository, properties, meterRegistry, clock);

    poller.runCatchUp();

    ConnectorHealthState state = healthRepository.current;
    assertNotNull(state);
    assertEquals(ConnectorHealthStatus.DEGRADED, state.status());
    assertEquals("HTTP_429", state.lastErrorCode());
    assertEquals(Instant.parse("2026-02-25T11:59:00Z"), state.lastSuccessAt());
    assertEquals(4, state.openOrdersFetched());
    assertEquals(7, state.recentTradesFetched());
    assertEquals(1.0, meterRegistry.counter("worker.connector.errors.total", "connector", "binance-spot", "operation", "catchup_run", "error", "HTTP_429").count());
  }

  private static final class InMemoryConnectorHealthRepository implements ConnectorHealthRepository {
    private ConnectorHealthState current;

    @Override
    public Optional<ConnectorHealthState> findByConnectorName(String connectorName) {
      return Optional.ofNullable(current);
    }

    @Override
    public void upsert(ConnectorHealthState state) {
      this.current = state;
    }
  }
}
