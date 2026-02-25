package com.tradingplatform.worker.connector;

import com.tradingplatform.integration.binance.BinanceConnectorException;
import com.tradingplatform.integration.binance.BinanceOpenOrderSnapshot;
import com.tradingplatform.integration.binance.BinancePollingClient;
import com.tradingplatform.integration.binance.BinanceTradeSnapshot;
import com.tradingplatform.worker.execution.BinanceConnectorProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(BinancePollingClient.class)
@ConditionalOnProperty(prefix = "connector.binance", name = "enabled", havingValue = "true")
public class BinanceCatchUpPoller {
  private static final Logger log = LoggerFactory.getLogger(BinanceCatchUpPoller.class);
  private static final String CONNECTOR_NAME = "binance-spot";

  private static final String POLL_TOTAL_METRIC = "worker.connector.poll.total";
  private static final String POLL_DURATION_METRIC = "worker.connector.poll.duration";
  private static final String POLL_ERROR_METRIC = "worker.connector.errors.total";

  private final BinancePollingClient pollingClient;
  private final ActiveInstrumentRepository activeInstrumentRepository;
  private final ConnectorHealthRepository healthRepository;
  private final BinanceConnectorProperties properties;
  private final MeterRegistry meterRegistry;
  private final Clock clock;

  public BinanceCatchUpPoller(
      BinancePollingClient pollingClient,
      ActiveInstrumentRepository activeInstrumentRepository,
      ConnectorHealthRepository healthRepository,
      BinanceConnectorProperties properties,
      MeterRegistry meterRegistry) {
    this(
        pollingClient,
        activeInstrumentRepository,
        healthRepository,
        properties,
        meterRegistry,
        Clock.systemUTC());
  }

  BinanceCatchUpPoller(
      BinancePollingClient pollingClient,
      ActiveInstrumentRepository activeInstrumentRepository,
      ConnectorHealthRepository healthRepository,
      BinanceConnectorProperties properties,
      MeterRegistry meterRegistry,
      Clock clock) {
    this.pollingClient = pollingClient;
    this.activeInstrumentRepository = activeInstrumentRepository;
    this.healthRepository = healthRepository;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runAtStartup() {
    runCatchUp();
  }

  @Scheduled(fixedDelayString = "${connector.binance.catchup.fixed-delay-ms:30000}")
  public void runScheduled() {
    runCatchUp();
  }

  public void runCatchUp() {
    Instant startedAt = clock.instant();
    ConnectorHealthState previous =
        healthRepository
            .findByConnectorName(CONNECTOR_NAME)
            .orElse(ConnectorHealthState.initial(CONNECTOR_NAME, startedAt));

    healthRepository.upsert(
        new ConnectorHealthState(
            CONNECTOR_NAME,
            previous.status(),
            previous.lastSuccessAt(),
            startedAt,
            null,
            previous.lastErrorAt(),
            previous.lastErrorCode(),
            previous.lastErrorMessage(),
            previous.openOrdersFetched(),
            previous.recentTradesFetched(),
            startedAt));

    try {
      List<BinanceOpenOrderSnapshot> openOrders =
          timedOperation("open_orders", pollingClient::fetchOpenOrders);
      List<String> symbols = activeInstrumentRepository.findActiveSymbols();
      Instant fromInclusive =
          startedAt.minus(
              Duration.ofMinutes(
                  Math.max(1L, properties.getCatchup().getRecentTradesLookbackMinutes())));

      int recentTradesFetched = 0;
      for (String symbol : symbols) {
        List<BinanceTradeSnapshot> tradesForSymbol =
            timedOperation("recent_trades", () -> pollingClient.fetchRecentTrades(symbol, fromInclusive));
        recentTradesFetched += tradesForSymbol.size();
      }

      Instant completedAt = clock.instant();
      healthRepository.upsert(
          new ConnectorHealthState(
              CONNECTOR_NAME,
              ConnectorHealthStatus.UP,
              completedAt,
              startedAt,
              completedAt,
              null,
              null,
              null,
              openOrders.size(),
              recentTradesFetched,
              completedAt));
      incrementTotal("catchup_run", "success");
      recordDuration("catchup_run", startedAt, completedAt);
      log.info(
          "Connector catch-up completed connector={} openOrdersFetched={} recentTradesFetched={} symbolsPolled={}",
          CONNECTOR_NAME,
          openOrders.size(),
          recentTradesFetched,
          symbols.size());
    } catch (RuntimeException ex) {
      Instant completedAt = clock.instant();
      String errorCode = errorCode(ex);
      healthRepository.upsert(
          new ConnectorHealthState(
              CONNECTOR_NAME,
              failureStatus(previous.lastSuccessAt(), completedAt),
              previous.lastSuccessAt(),
              startedAt,
              completedAt,
              completedAt,
              errorCode,
              sanitizeMessage(ex),
              previous.openOrdersFetched(),
              previous.recentTradesFetched(),
              completedAt));
      incrementTotal("catchup_run", "failure");
      meterRegistry
          .counter(
              POLL_ERROR_METRIC,
              "connector",
              CONNECTOR_NAME,
              "operation",
              "catchup_run",
              "error",
              errorCode)
          .increment();
      recordDuration("catchup_run", startedAt, completedAt);
      log.warn("Connector catch-up failed connector={} error={}", CONNECTOR_NAME, errorCode, ex);
    }
  }

  private <T> T timedOperation(String operation, Supplier<T> supplier) {
    Instant startedAt = clock.instant();
    try {
      T value = supplier.get();
      incrementTotal(operation, "success");
      recordDuration(operation, startedAt, clock.instant());
      return value;
    } catch (RuntimeException ex) {
      String errorCode = errorCode(ex);
      incrementTotal(operation, "failure");
      meterRegistry
          .counter(
              POLL_ERROR_METRIC,
              "connector",
              CONNECTOR_NAME,
              "operation",
              operation,
              "error",
              errorCode)
          .increment();
      recordDuration(operation, startedAt, clock.instant());
      throw ex;
    }
  }

  private void incrementTotal(String operation, String outcome) {
    meterRegistry
        .counter(
            POLL_TOTAL_METRIC,
            "connector",
            CONNECTOR_NAME,
            "operation",
            operation,
            "outcome",
            outcome)
        .increment();
  }

  private void recordDuration(String operation, Instant startedAt, Instant completedAt) {
    Timer.builder(POLL_DURATION_METRIC)
        .description("Connector polling latency")
        .tag("connector", CONNECTOR_NAME)
        .tag("operation", operation)
        .register(meterRegistry)
        .record(Duration.between(startedAt, completedAt).abs());
  }

  private ConnectorHealthStatus failureStatus(Instant lastSuccessAt, Instant now) {
    if (lastSuccessAt == null) {
      return ConnectorHealthStatus.DOWN;
    }
    long thresholdMinutes = Math.max(1L, properties.getHealth().getDownThresholdMinutes());
    Duration sinceLastSuccess = Duration.between(lastSuccessAt, now);
    if (sinceLastSuccess.toMinutes() >= thresholdMinutes) {
      return ConnectorHealthStatus.DOWN;
    }
    return ConnectorHealthStatus.DEGRADED;
  }

  private static String errorCode(Throwable error) {
    if (error instanceof BinanceConnectorException ex && ex.httpStatus() > 0) {
      return "HTTP_" + ex.httpStatus();
    }
    String simpleName = error.getClass().getSimpleName();
    return simpleName == null || simpleName.isBlank() ? "UnknownError" : simpleName;
  }

  private static String sanitizeMessage(Throwable error) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) {
      return errorCode(error);
    }
    String compact = message.replaceAll("\\s+", " ").trim();
    if (compact.length() <= 500) {
      return compact;
    }
    return compact.substring(0, 500);
  }
}
