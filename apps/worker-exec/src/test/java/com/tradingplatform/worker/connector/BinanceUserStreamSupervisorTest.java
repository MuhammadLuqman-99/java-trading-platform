package com.tradingplatform.worker.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tradingplatform.integration.binance.BinanceExecutionReportEvent;
import com.tradingplatform.integration.binance.BinanceUserStreamClient;
import com.tradingplatform.integration.binance.BinanceUserStreamEventHandler;
import com.tradingplatform.worker.execution.ingestion.ExecutionIngestionResult;
import com.tradingplatform.worker.execution.ingestion.ExecutionReportIngestionPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BinanceUserStreamSupervisorTest {
  @Test
  void shouldMarkWsStateUpOnConnected() {
    InMemoryConnectorHealthRepository repository = new InMemoryConnectorHealthRepository();
    StubUserStreamClient client = new StubUserStreamClient();
    client.reconnectAttempts = 3L;
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:10:00Z"), ZoneOffset.UTC);
    StubExecutionReportIngestionPort ingestionPort = new StubExecutionReportIngestionPort();
    BinanceUserStreamSupervisor supervisor =
        new BinanceUserStreamSupervisor(
            client, repository, new SimpleMeterRegistry(), clock, ingestionPort);

    supervisor.onConnected("listen-key-abcdef");

    ConnectorHealthState state = repository.current;
    assertNotNull(state);
    assertEquals(ConnectorWsConnectionState.UP, state.wsConnectionState());
    assertEquals(3L, state.wsReconnectAttempts());
    assertEquals(Instant.parse("2026-02-25T12:10:00Z"), state.lastWsConnectedAt());
  }

  @Test
  void shouldTrackWsErrorsAndExecutionReports() {
    InMemoryConnectorHealthRepository repository = new InMemoryConnectorHealthRepository();
    StubUserStreamClient client = new StubUserStreamClient();
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:20:00Z"), ZoneOffset.UTC);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    StubExecutionReportIngestionPort ingestionPort = new StubExecutionReportIngestionPort();
    BinanceUserStreamSupervisor supervisor =
        new BinanceUserStreamSupervisor(client, repository, meterRegistry, clock, ingestionPort);

    supervisor.onError("HTTP_429", "rate limited", new RuntimeException("rate limited"));
    String rawPayload = "{\"e\":\"executionReport\",\"x\":\"TRADE\",\"t\":501}";
    supervisor.onExecutionReport(
        new BinanceExecutionReportEvent(
            "BTCUSDT",
            "1001",
            "6b8b4567-1234-4bba-a57c-f945f2999d01",
            "501",
            "BUY",
            "TRADE",
            "PARTIALLY_FILLED",
            new BigDecimal("0.005"),
            new BigDecimal("0.005"),
            new BigDecimal("42100"),
            new BigDecimal("0.01"),
            new BigDecimal("42000"),
            new BigDecimal("0.21"),
            "USDT",
            Instant.parse("2026-02-25T12:20:00Z"),
            Instant.parse("2026-02-25T12:20:01Z"),
            rawPayload));

    ConnectorHealthState state = repository.current;
    assertNotNull(state);
    assertEquals(ConnectorWsConnectionState.DEGRADED, state.wsConnectionState());
    assertEquals("HTTP_429", state.lastWsErrorCode());
    assertEquals("rate limited", state.lastWsErrorMessage());
    assertEquals(rawPayload, ingestionPort.lastPayload);
    assertTrue(
        meterRegistry
                .counter(
                    "worker.connector.ws.execution_report.total",
                    "connector",
                    "binance-spot",
                    "outcome",
                    "processed")
                .count()
            > 0.0);
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

  private static final class StubUserStreamClient implements BinanceUserStreamClient {
    private long reconnectAttempts;

    @Override
    public void start(BinanceUserStreamEventHandler eventHandler) {}

    @Override
    public void stop() {}

    @Override
    public boolean isConnected() {
      return false;
    }

    @Override
    public long reconnectAttempts() {
      return reconnectAttempts;
    }
  }

  private static final class StubExecutionReportIngestionPort
      implements ExecutionReportIngestionPort {
    private String lastPayload;

    @Override
    public ExecutionIngestionResult ingest(String rawExecutionReportPayload) {
      this.lastPayload = rawExecutionReportPayload;
      return ExecutionIngestionResult.PROCESSED;
    }
  }
}
