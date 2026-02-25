package com.tradingplatform.worker.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.tradingplatform.integration.binance.BinanceConnectorException;
import com.tradingplatform.integration.binance.BinanceOpenOrderSnapshot;
import com.tradingplatform.integration.binance.BinancePollingClient;
import com.tradingplatform.integration.binance.BinanceTradeSnapshot;
import com.tradingplatform.worker.execution.BinanceConnectorProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BinanceCatchUpPollerTest {
  @Test
  void shouldMarkConnectorUpWhenCatchUpSucceeds() {
    BinancePollingClient pollingClient = org.mockito.Mockito.mock(BinancePollingClient.class);
    BinanceFillProcessor fillProcessor = org.mockito.Mockito.mock(BinanceFillProcessor.class);
    when(fillProcessor.processTrade(org.mockito.ArgumentMatchers.any()))
        .thenReturn(FillProcessingOutcome.INSERTED);
    when(pollingClient.fetchOpenOrders())
        .thenReturn(
            List.of(
                new BinanceOpenOrderSnapshot("BTCUSDT", "ord-1", "1001", "NEW"),
                new BinanceOpenOrderSnapshot("ETHUSDT", "ord-2", "1002", "NEW")));
    when(pollingClient.fetchRecentTrades("BTCUSDT", Instant.parse("2026-02-25T11:30:00Z")))
        .thenReturn(
            List.of(
                new BinanceTradeSnapshot(
                    "BTCUSDT",
                    "501",
                    "1001",
                    "BUY",
                    new BigDecimal("0.01"),
                    new BigDecimal("42500"),
                    "USDT",
                    new BigDecimal("0.5"),
                    Instant.now())));
    when(pollingClient.fetchRecentTrades("ETHUSDT", Instant.parse("2026-02-25T11:30:00Z")))
        .thenReturn(
            List.of(
                new BinanceTradeSnapshot(
                    "ETHUSDT",
                    "601",
                    "1002",
                    "SELL",
                    new BigDecimal("0.5"),
                    new BigDecimal("2300"),
                    "USDT",
                    new BigDecimal("1.2"),
                    Instant.now()),
                new BinanceTradeSnapshot(
                    "ETHUSDT",
                    "602",
                    "1003",
                    "BUY",
                    new BigDecimal("0.25"),
                    new BigDecimal("2290"),
                    "USDT",
                    new BigDecimal("0.6"),
                    Instant.now())));

    InMemoryConnectorHealthRepository healthRepository = new InMemoryConnectorHealthRepository();
    InMemoryConnectorReplayRequestRepository replayRepository =
        new InMemoryConnectorReplayRequestRepository();
    ActiveInstrumentRepository instrumentRepository = () -> List.of("BTCUSDT", "ETHUSDT");
    BinanceConnectorProperties properties = new BinanceConnectorProperties();
    properties.getCatchup().setRecentTradesLookbackMinutes(30);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:00:00Z"), ZoneOffset.UTC);

    BinanceCatchUpPoller poller =
        new BinanceCatchUpPoller(
            pollingClient,
            instrumentRepository,
            healthRepository,
            replayRepository,
            fillProcessor,
            properties,
            meterRegistry,
            clock);

    poller.runCatchUp();

    ConnectorHealthState state = healthRepository.current;
    assertNotNull(state);
    assertEquals("binance-spot", state.connectorName());
    assertEquals(ConnectorHealthStatus.UP, state.status());
    assertEquals(2, state.openOrdersFetched());
    assertEquals(3, state.recentTradesFetched());
    assertNull(state.lastErrorCode());
    assertNull(state.lastErrorMessage());
    assertEquals(
        1.0,
        meterRegistry
            .counter(
                "worker.connector.poll.total",
                "connector",
                "binance-spot",
                "operation",
                "open_orders",
                "outcome",
                "success")
            .count());
    assertEquals(1, replayRepository.pendingRecoveryCount());
  }

  @Test
  void shouldMarkConnectorDegradedOnFailureAfterRecentSuccess() {
    BinancePollingClient pollingClient = org.mockito.Mockito.mock(BinancePollingClient.class);
    BinanceFillProcessor fillProcessor = org.mockito.Mockito.mock(BinanceFillProcessor.class);
    when(pollingClient.fetchOpenOrders())
        .thenThrow(new BinanceConnectorException("rate limit", 429, -1003));

    InMemoryConnectorHealthRepository healthRepository = new InMemoryConnectorHealthRepository();
    InMemoryConnectorReplayRequestRepository replayRepository =
        new InMemoryConnectorReplayRequestRepository();
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
            ConnectorWsConnectionState.DOWN,
            null,
            null,
            null,
            null,
            null,
            0L,
            Instant.parse("2026-02-25T11:59:00Z"));
    ActiveInstrumentRepository instrumentRepository = List::of;
    BinanceConnectorProperties properties = new BinanceConnectorProperties();
    properties.getHealth().setDownThresholdMinutes(5);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:00:00Z"), ZoneOffset.UTC);

    BinanceCatchUpPoller poller =
        new BinanceCatchUpPoller(
            pollingClient,
            instrumentRepository,
            healthRepository,
            replayRepository,
            fillProcessor,
            properties,
            meterRegistry,
            clock);

    poller.runCatchUp();

    ConnectorHealthState state = healthRepository.current;
    assertNotNull(state);
    assertEquals(ConnectorHealthStatus.DEGRADED, state.status());
    assertEquals("HTTP_429", state.lastErrorCode());
    assertEquals(Instant.parse("2026-02-25T11:59:00Z"), state.lastSuccessAt());
    assertEquals(4, state.openOrdersFetched());
    assertEquals(7, state.recentTradesFetched());
    assertEquals(
        1.0,
        meterRegistry
            .counter(
                "worker.connector.errors.total",
                "connector",
                "binance-spot",
                "operation",
                "catchup_run",
                "error",
                "HTTP_429")
            .count());
    assertEquals(0, replayRepository.pendingRecoveryCount());
  }

  @Test
  void shouldProcessManualReplayQueueRequest() {
    BinancePollingClient pollingClient = org.mockito.Mockito.mock(BinancePollingClient.class);
    BinanceFillProcessor fillProcessor = org.mockito.Mockito.mock(BinanceFillProcessor.class);
    when(pollingClient.fetchOpenOrders()).thenReturn(List.of());

    InMemoryConnectorHealthRepository healthRepository = new InMemoryConnectorHealthRepository();
    InMemoryConnectorReplayRequestRepository replayRepository =
        new InMemoryConnectorReplayRequestRepository();
    replayRepository.enqueueManual("binance-spot", "manual_replay", Instant.parse("2026-02-25T11:59:00Z"));
    ActiveInstrumentRepository instrumentRepository = List::of;
    BinanceConnectorProperties properties = new BinanceConnectorProperties();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:00:00Z"), ZoneOffset.UTC);

    BinanceCatchUpPoller poller =
        new BinanceCatchUpPoller(
            pollingClient,
            instrumentRepository,
            healthRepository,
            replayRepository,
            fillProcessor,
            properties,
            meterRegistry,
            clock);

    poller.runReplayQueue();

    ConnectorReplayRequest request = replayRepository.first();
    assertEquals(ConnectorReplayRequestStatus.SUCCEEDED, request.status());
    assertNotNull(request.startedAt());
    assertNotNull(request.completedAt());
    assertEquals(
        1.0,
        meterRegistry
            .counter(
                "worker.connector.replay.requests.total",
                "connector",
                "binance-spot",
                "trigger_type",
                "manual",
                "outcome",
                "success")
            .count());
    double queueDepth =
        meterRegistry
            .get("worker.connector.replay.queue.depth")
            .tag("connector", "binance-spot")
            .gauge()
            .value();
    assertEquals(0.0, queueDepth);
  }

  @Test
  void shouldSkipDuplicateRecoveryReplayWithinDedupeWindow() {
    BinancePollingClient pollingClient = org.mockito.Mockito.mock(BinancePollingClient.class);
    BinanceFillProcessor fillProcessor = org.mockito.Mockito.mock(BinanceFillProcessor.class);
    when(pollingClient.fetchOpenOrders()).thenReturn(List.of());

    InMemoryConnectorHealthRepository healthRepository = new InMemoryConnectorHealthRepository();
    healthRepository.current =
        new ConnectorHealthState(
            "binance-spot",
            ConnectorHealthStatus.DEGRADED,
            Instant.parse("2026-02-25T11:50:00Z"),
            null,
            null,
            Instant.parse("2026-02-25T11:55:00Z"),
            "HTTP_429",
            "rate limit",
            0,
            0,
            ConnectorWsConnectionState.DOWN,
            null,
            null,
            null,
            null,
            null,
            0L,
            Instant.parse("2026-02-25T11:55:00Z"));
    InMemoryConnectorReplayRequestRepository replayRepository =
        new InMemoryConnectorReplayRequestRepository();
    ActiveInstrumentRepository instrumentRepository = List::of;
    BinanceConnectorProperties properties = new BinanceConnectorProperties();
    properties.getCatchup().setRecoveryDedupeWindowMs(Duration.ofMinutes(5).toMillis());
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:00:00Z"), ZoneOffset.UTC);

    BinanceCatchUpPoller poller =
        new BinanceCatchUpPoller(
            pollingClient,
            instrumentRepository,
            healthRepository,
            replayRepository,
            fillProcessor,
            properties,
            meterRegistry,
            clock);

    poller.runCatchUp();
    poller.runCatchUp();

    assertEquals(1, replayRepository.pendingRecoveryCount());
    assertTrue(
        replayRepository.all().stream()
            .allMatch(request -> request.triggerType() == ConnectorReplayTriggerType.RECOVERY));
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

  private static final class InMemoryConnectorReplayRequestRepository
      implements ConnectorReplayRequestRepository {
    private final Map<UUID, ConnectorReplayRequest> requests = new LinkedHashMap<>();

    @Override
    public Optional<ConnectorReplayRequest> claimNextPending(String connectorName, Instant claimedAt) {
      return requests.values().stream()
          .filter(request -> connectorName.equals(request.connectorName()))
          .filter(request -> request.status() == ConnectorReplayRequestStatus.PENDING)
          .min(Comparator.comparing(ConnectorReplayRequest::requestedAt))
          .map(
              request -> {
                ConnectorReplayRequest updated =
                    new ConnectorReplayRequest(
                        request.id(),
                        request.connectorName(),
                        request.triggerType(),
                        request.reason(),
                        ConnectorReplayRequestStatus.RUNNING,
                        request.requestedBy(),
                        request.requestedAt(),
                        claimedAt,
                        request.completedAt(),
                        request.errorCode(),
                        request.errorMessage());
                requests.put(request.id(), updated);
                return updated;
              });
    }

    @Override
    public void markSucceeded(UUID requestId, Instant completedAt) {
      ConnectorReplayRequest existing = requests.get(requestId);
      if (existing == null) {
        return;
      }
      requests.put(
          requestId,
          new ConnectorReplayRequest(
              existing.id(),
              existing.connectorName(),
              existing.triggerType(),
              existing.reason(),
              ConnectorReplayRequestStatus.SUCCEEDED,
              existing.requestedBy(),
              existing.requestedAt(),
              existing.startedAt(),
              completedAt,
              null,
              null));
    }

    @Override
    public void markFailed(
        UUID requestId, String errorCode, String errorMessage, Instant completedAt) {
      ConnectorReplayRequest existing = requests.get(requestId);
      if (existing == null) {
        return;
      }
      requests.put(
          requestId,
          new ConnectorReplayRequest(
              existing.id(),
              existing.connectorName(),
              existing.triggerType(),
              existing.reason(),
              ConnectorReplayRequestStatus.FAILED,
              existing.requestedBy(),
              existing.requestedAt(),
              existing.startedAt(),
              completedAt,
              errorCode,
              errorMessage));
    }

    @Override
    public boolean enqueueRecoveryIfWindowClear(
        String connectorName,
        String reason,
        String requestedBy,
        Instant requestedAt,
        Duration dedupeWindow) {
      Instant windowStart = requestedAt.minusMillis(Math.max(0L, dedupeWindow.toMillis()));
      boolean exists =
          requests.values().stream()
              .filter(request -> connectorName.equals(request.connectorName()))
              .filter(request -> request.triggerType() == ConnectorReplayTriggerType.RECOVERY)
              .anyMatch(request -> !request.requestedAt().isBefore(windowStart));
      if (exists) {
        return false;
      }
      UUID id = UUID.randomUUID();
      requests.put(
          id,
          new ConnectorReplayRequest(
              id,
              connectorName,
              ConnectorReplayTriggerType.RECOVERY,
              reason,
              ConnectorReplayRequestStatus.PENDING,
              requestedBy,
              requestedAt,
              null,
              null,
              null,
              null));
      return true;
    }

    @Override
    public int countPending(String connectorName) {
      return (int)
          requests.values().stream()
              .filter(request -> connectorName.equals(request.connectorName()))
              .filter(request -> request.status() == ConnectorReplayRequestStatus.PENDING)
              .count();
    }

    void enqueueManual(String connectorName, String reason, Instant requestedAt) {
      UUID id = UUID.randomUUID();
      requests.put(
          id,
          new ConnectorReplayRequest(
              id,
              connectorName,
              ConnectorReplayTriggerType.MANUAL,
              reason,
              ConnectorReplayRequestStatus.PENDING,
              "admin",
              requestedAt,
              null,
              null,
              null,
              null));
    }

    ConnectorReplayRequest first() {
      return requests.values().iterator().next();
    }

    List<ConnectorReplayRequest> all() {
      return List.copyOf(requests.values());
    }

    int pendingRecoveryCount() {
      return (int)
          requests.values().stream()
              .filter(request -> request.status() == ConnectorReplayRequestStatus.PENDING)
              .filter(request -> request.triggerType() == ConnectorReplayTriggerType.RECOVERY)
              .count();
    }
  }
}
