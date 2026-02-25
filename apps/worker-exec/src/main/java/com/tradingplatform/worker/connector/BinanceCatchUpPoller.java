package com.tradingplatform.worker.connector;

import com.tradingplatform.integration.binance.BinanceConnectorException;
import com.tradingplatform.integration.binance.BinanceOpenOrderSnapshot;
import com.tradingplatform.integration.binance.BinancePollingClient;
import com.tradingplatform.integration.binance.BinanceTradeSnapshot;
import com.tradingplatform.worker.execution.BinanceConnectorProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
  private static final String EXECUTIONS_PROCESSED_TOTAL_METRIC =
      "worker.executions.processed.total";
  private static final String EXECUTIONS_PROCESS_DURATION_METRIC =
      "worker.executions.process.duration";
  private static final String REPLAY_TOTAL_METRIC = "worker.connector.replay.requests.total";
  private static final String REPLAY_DURATION_METRIC = "worker.connector.replay.duration";
  private static final String REPLAY_QUEUE_DEPTH_METRIC = "worker.connector.replay.queue.depth";
  private static final String RECOVERY_REASON = "auto_recovery_reconcile";
  private static final String RECOVERY_REQUESTED_BY = "system-recovery";

  private final BinancePollingClient pollingClient;
  private final ActiveInstrumentRepository activeInstrumentRepository;
  private final ConnectorHealthRepository healthRepository;
  private final ConnectorReplayRequestRepository replayRequestRepository;
  private final BinanceFillProcessor fillProcessor;
  private final BinanceConnectorProperties properties;
  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final AtomicBoolean catchUpInProgress = new AtomicBoolean(false);
  private final AtomicInteger pendingReplayGauge;

  @Autowired
  public BinanceCatchUpPoller(
      BinancePollingClient pollingClient,
      ActiveInstrumentRepository activeInstrumentRepository,
      ConnectorHealthRepository healthRepository,
      ConnectorReplayRequestRepository replayRequestRepository,
      BinanceFillProcessor fillProcessor,
      BinanceConnectorProperties properties,
      MeterRegistry meterRegistry) {
    this(
        pollingClient,
        activeInstrumentRepository,
        healthRepository,
        replayRequestRepository,
        fillProcessor,
        properties,
        meterRegistry,
        Clock.systemUTC());
  }

  BinanceCatchUpPoller(
      BinancePollingClient pollingClient,
      ActiveInstrumentRepository activeInstrumentRepository,
      ConnectorHealthRepository healthRepository,
      ConnectorReplayRequestRepository replayRequestRepository,
      BinanceFillProcessor fillProcessor,
      BinanceConnectorProperties properties,
      MeterRegistry meterRegistry,
      Clock clock) {
    this.pollingClient = pollingClient;
    this.activeInstrumentRepository = activeInstrumentRepository;
    this.healthRepository = healthRepository;
    this.replayRequestRepository = replayRequestRepository;
    this.fillProcessor = fillProcessor;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
    this.pendingReplayGauge =
        meterRegistry.gauge(
            REPLAY_QUEUE_DEPTH_METRIC, Tags.of("connector", CONNECTOR_NAME), new AtomicInteger(0));
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runAtStartup() {
    runCatchUp(CatchUpTrigger.STARTUP, null);
  }

  @Scheduled(fixedDelayString = "${connector.binance.catchup.fixed-delay-ms:30000}")
  public void runScheduled() {
    runCatchUp(CatchUpTrigger.SCHEDULED, null);
  }

  @Scheduled(fixedDelayString = "${connector.binance.catchup.replay-poll-delay-ms:5000}")
  public void runReplayQueue() {
    if (!catchUpInProgress.compareAndSet(false, true)) {
      refreshPendingReplayGauge();
      log.info("Skipping connector replay queue poll connector={} because catch-up is already running", CONNECTOR_NAME);
      return;
    }
    try {
      Instant claimedAt = clock.instant();
      Optional<ConnectorReplayRequest> claimed =
          replayRequestRepository.claimNextPending(CONNECTOR_NAME, claimedAt);
      if (claimed.isEmpty()) {
        return;
      }
      ConnectorReplayRequest request = claimed.get();
      CatchUpTrigger trigger = triggerFor(request.triggerType());
      Instant requestStartedAt = clock.instant();
      log.info(
          "Connector replay request started connector={} requestId={} triggerType={} reason={}",
          CONNECTOR_NAME,
          request.id(),
          request.triggerType(),
          request.reason());

      CatchUpOutcome outcome = executeCatchUp(trigger, request.id());
      if (outcome.success()) {
        replayRequestRepository.markSucceeded(request.id(), outcome.completedAt());
        log.info(
            "Connector replay request succeeded connector={} requestId={} triggerType={}",
            CONNECTOR_NAME,
            request.id(),
            request.triggerType());
      } else {
        replayRequestRepository.markFailed(
            request.id(), outcome.errorCode(), outcome.errorMessage(), outcome.completedAt());
        log.warn(
            "Connector replay request failed connector={} requestId={} triggerType={} error={}",
            CONNECTOR_NAME,
            request.id(),
            request.triggerType(),
            outcome.errorCode());
      }
      incrementReplayTotal(request.triggerType(), outcome.success() ? "success" : "failure");
      recordReplayDuration(request.triggerType(), requestStartedAt, outcome.completedAt());
    } finally {
      catchUpInProgress.set(false);
      refreshPendingReplayGauge();
    }
  }

  public void runCatchUp() {
    runCatchUp(CatchUpTrigger.SCHEDULED, null);
  }

  private void runCatchUp(CatchUpTrigger trigger, UUID replayRequestId) {
    if (!catchUpInProgress.compareAndSet(false, true)) {
      log.info("Skipping connector catch-up trigger={} connector={} because another run is in progress", trigger, CONNECTOR_NAME);
      return;
    }
    try {
      executeCatchUp(trigger, replayRequestId);
    } finally {
      catchUpInProgress.set(false);
      refreshPendingReplayGauge();
    }
  }

  private CatchUpOutcome executeCatchUp(CatchUpTrigger trigger, UUID replayRequestId) {
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
            previous.wsConnectionState(),
            previous.lastWsConnectedAt(),
            previous.lastWsDisconnectedAt(),
            previous.lastWsErrorAt(),
            previous.lastWsErrorCode(),
            previous.lastWsErrorMessage(),
            previous.wsReconnectAttempts(),
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
        for (BinanceTradeSnapshot trade : tradesForSymbol) {
          processTradeSnapshot(trade);
        }
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
              previous.wsConnectionState(),
              previous.lastWsConnectedAt(),
              previous.lastWsDisconnectedAt(),
              previous.lastWsErrorAt(),
              previous.lastWsErrorCode(),
              previous.lastWsErrorMessage(),
              previous.wsReconnectAttempts(),
              completedAt));
      incrementTotal("catchup_run", "success");
      recordDuration("catchup_run", startedAt, completedAt);
      if (shouldQueueRecoveryReplay(trigger, previous.status())) {
        maybeQueueRecoveryReplay(completedAt);
      }
      log.info(
          "Connector catch-up completed connector={} trigger={} replayRequestId={} openOrdersFetched={} recentTradesFetched={} symbolsPolled={}",
          CONNECTOR_NAME,
          trigger,
          replayRequestId,
          openOrders.size(),
          recentTradesFetched,
          symbols.size());
      return new CatchUpOutcome(true, completedAt, null, null);
    } catch (RuntimeException ex) {
      Instant completedAt = clock.instant();
      String errorCode = errorCode(ex);
      String errorMessage = sanitizeMessage(ex);
      healthRepository.upsert(
          new ConnectorHealthState(
              CONNECTOR_NAME,
              failureStatus(previous.lastSuccessAt(), completedAt),
              previous.lastSuccessAt(),
              startedAt,
              completedAt,
              completedAt,
              errorCode,
              errorMessage,
              previous.openOrdersFetched(),
              previous.recentTradesFetched(),
              previous.wsConnectionState(),
              previous.lastWsConnectedAt(),
              previous.lastWsDisconnectedAt(),
              previous.lastWsErrorAt(),
              previous.lastWsErrorCode(),
              previous.lastWsErrorMessage(),
              previous.wsReconnectAttempts(),
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
      log.warn(
          "Connector catch-up failed connector={} trigger={} replayRequestId={} error={}",
          CONNECTOR_NAME,
          trigger,
          replayRequestId,
          errorCode,
          ex);
      return new CatchUpOutcome(false, completedAt, errorCode, errorMessage);
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

  private void processTradeSnapshot(BinanceTradeSnapshot trade) {
    Instant startedAt = clock.instant();
    try {
      FillProcessingOutcome outcome = fillProcessor.processTrade(trade);
      meterRegistry
          .counter(
              EXECUTIONS_PROCESSED_TOTAL_METRIC,
              "connector",
              CONNECTOR_NAME,
              "outcome",
              outcome.metricTag())
          .increment();
    } catch (RuntimeException ex) {
      String errorCode = errorCode(ex);
      meterRegistry
          .counter(
              EXECUTIONS_PROCESSED_TOTAL_METRIC,
              "connector",
              CONNECTOR_NAME,
              "outcome",
              "failed")
          .increment();
      meterRegistry
          .counter(
              POLL_ERROR_METRIC,
              "connector",
              CONNECTOR_NAME,
              "operation",
              "trade_processing",
              "error",
              errorCode)
          .increment();
      log.warn(
          "Connector trade processing failed connector={} symbol={} tradeId={} error={}",
          CONNECTOR_NAME,
          trade.symbol(),
          trade.tradeId(),
          errorCode,
          ex);
    } finally {
      Timer.builder(EXECUTIONS_PROCESS_DURATION_METRIC)
          .description("Connector trade processing latency")
          .tag("connector", CONNECTOR_NAME)
          .register(meterRegistry)
          .record(Duration.between(startedAt, clock.instant()).abs());
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

  private void incrementReplayTotal(ConnectorReplayTriggerType triggerType, String outcome) {
    meterRegistry
        .counter(
            REPLAY_TOTAL_METRIC,
            "connector",
            CONNECTOR_NAME,
            "trigger_type",
            triggerType.name().toLowerCase(),
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

  private void recordReplayDuration(
      ConnectorReplayTriggerType triggerType, Instant startedAt, Instant completedAt) {
    Timer.builder(REPLAY_DURATION_METRIC)
        .description("Connector replay request processing latency")
        .tag("connector", CONNECTOR_NAME)
        .tag("trigger_type", triggerType.name().toLowerCase())
        .register(meterRegistry)
        .record(Duration.between(startedAt, completedAt).abs());
  }

  private void maybeQueueRecoveryReplay(Instant requestedAt) {
    long dedupeWindowMs = Math.max(0L, properties.getCatchup().getRecoveryDedupeWindowMs());
    Duration dedupeWindow = Duration.ofMillis(dedupeWindowMs);
    boolean queued =
        replayRequestRepository.enqueueRecoveryIfWindowClear(
            CONNECTOR_NAME, RECOVERY_REASON, RECOVERY_REQUESTED_BY, requestedAt, dedupeWindow);
    if (queued) {
      log.info(
          "Connector recovery replay request queued connector={} dedupeWindowMs={}",
          CONNECTOR_NAME,
          dedupeWindowMs);
    } else {
      log.info(
          "Connector recovery replay request skipped by dedupe connector={} dedupeWindowMs={}",
          CONNECTOR_NAME,
          dedupeWindowMs);
    }
  }

  private boolean shouldQueueRecoveryReplay(
      CatchUpTrigger trigger, ConnectorHealthStatus previousStatus) {
    if (previousStatus == null || previousStatus == ConnectorHealthStatus.UP) {
      return false;
    }
    return trigger == CatchUpTrigger.STARTUP || trigger == CatchUpTrigger.SCHEDULED;
  }

  private void refreshPendingReplayGauge() {
    try {
      int pending = replayRequestRepository.countPending(CONNECTOR_NAME);
      pendingReplayGauge.set(Math.max(0, pending));
    } catch (RuntimeException ex) {
      log.debug("Unable to refresh connector replay queue gauge connector={}", CONNECTOR_NAME, ex);
    }
  }

  private static CatchUpTrigger triggerFor(ConnectorReplayTriggerType triggerType) {
    if (triggerType == ConnectorReplayTriggerType.RECOVERY) {
      return CatchUpTrigger.RECOVERY_RECONNECT;
    }
    return CatchUpTrigger.MANUAL_REPLAY;
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

  private enum CatchUpTrigger {
    STARTUP,
    SCHEDULED,
    MANUAL_REPLAY,
    RECOVERY_RECONNECT
  }

  private record CatchUpOutcome(
      boolean success, Instant completedAt, String errorCode, String errorMessage) {}
}
