package com.tradingplatform.worker.connector;

import com.tradingplatform.integration.binance.BinanceExecutionReportEvent;
import com.tradingplatform.integration.binance.BinanceUserStreamClient;
import com.tradingplatform.integration.binance.BinanceUserStreamEventHandler;
import com.tradingplatform.worker.execution.ingestion.ExecutionIngestionResult;
import com.tradingplatform.worker.execution.ingestion.ExecutionReportIngestionPort;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(BinanceUserStreamClient.class)
@ConditionalOnProperty(prefix = "connector.binance", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "connector.binance.ws", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "worker.execution", name = "adapter", havingValue = "binance")
public class BinanceUserStreamSupervisor implements BinanceUserStreamEventHandler {
  private static final Logger log = LoggerFactory.getLogger(BinanceUserStreamSupervisor.class);
  private static final String CONNECTOR_NAME = "binance-spot";

  private final BinanceUserStreamClient userStreamClient;
  private final ConnectorHealthRepository healthRepository;
  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final ExecutionReportIngestionPort executionReportIngestionPort;

  public BinanceUserStreamSupervisor(
      BinanceUserStreamClient userStreamClient,
      ConnectorHealthRepository healthRepository,
      MeterRegistry meterRegistry,
      ExecutionReportIngestionPort executionReportIngestionPort) {
    this(
        userStreamClient,
        healthRepository,
        meterRegistry,
        Clock.systemUTC(),
        executionReportIngestionPort);
  }

  BinanceUserStreamSupervisor(
      BinanceUserStreamClient userStreamClient,
      ConnectorHealthRepository healthRepository,
      MeterRegistry meterRegistry,
      Clock clock,
      ExecutionReportIngestionPort executionReportIngestionPort) {
    this.userStreamClient = userStreamClient;
    this.healthRepository = healthRepository;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
    this.executionReportIngestionPort = executionReportIngestionPort;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    userStreamClient.start(this);
  }

  @PreDestroy
  public void stop() {
    userStreamClient.stop();
  }

  @Override
  public void onConnected(String listenKey) {
    Instant now = clock.instant();
    upsertState(
        previous ->
            new ConnectorHealthState(
                previous.connectorName(),
                previous.status(),
                previous.lastSuccessAt(),
                previous.lastPollStartedAt(),
                previous.lastPollCompletedAt(),
                previous.lastErrorAt(),
                previous.lastErrorCode(),
                previous.lastErrorMessage(),
                previous.openOrdersFetched(),
                previous.recentTradesFetched(),
                ConnectorWsConnectionState.UP,
                now,
                previous.lastWsDisconnectedAt(),
                previous.lastWsErrorAt(),
                previous.lastWsErrorCode(),
                previous.lastWsErrorMessage(),
                userStreamClient.reconnectAttempts(),
                now));
    log.info("Binance WS connected connector={} listenKeySuffix={}", CONNECTOR_NAME, maskListenKey(listenKey));
  }

  @Override
  public void onDisconnected(int statusCode, String reason) {
    Instant now = clock.instant();
    ConnectorWsConnectionState nextState =
        statusCode == 1000 ? ConnectorWsConnectionState.DOWN : ConnectorWsConnectionState.DEGRADED;
    upsertState(
        previous ->
            new ConnectorHealthState(
                previous.connectorName(),
                previous.status(),
                previous.lastSuccessAt(),
                previous.lastPollStartedAt(),
                previous.lastPollCompletedAt(),
                previous.lastErrorAt(),
                previous.lastErrorCode(),
                previous.lastErrorMessage(),
                previous.openOrdersFetched(),
                previous.recentTradesFetched(),
                nextState,
                previous.lastWsConnectedAt(),
                now,
                previous.lastWsErrorAt(),
                previous.lastWsErrorCode(),
                previous.lastWsErrorMessage(),
                userStreamClient.reconnectAttempts(),
                now));
    log.warn(
        "Binance WS disconnected connector={} statusCode={} reason={}",
        CONNECTOR_NAME,
        statusCode,
        reason);
  }

  @Override
  public void onReconnectScheduled(long reconnectAttempts, Duration delay) {
    Instant now = clock.instant();
    upsertState(
        previous ->
            new ConnectorHealthState(
                previous.connectorName(),
                previous.status(),
                previous.lastSuccessAt(),
                previous.lastPollStartedAt(),
                previous.lastPollCompletedAt(),
                previous.lastErrorAt(),
                previous.lastErrorCode(),
                previous.lastErrorMessage(),
                previous.openOrdersFetched(),
                previous.recentTradesFetched(),
                ConnectorWsConnectionState.CONNECTING,
                previous.lastWsConnectedAt(),
                previous.lastWsDisconnectedAt(),
                previous.lastWsErrorAt(),
                previous.lastWsErrorCode(),
                previous.lastWsErrorMessage(),
                reconnectAttempts,
                now));
    log.info(
        "Binance WS reconnect scheduled connector={} attempt={} delayMs={}",
        CONNECTOR_NAME,
        reconnectAttempts,
        delay.toMillis());
  }

  @Override
  public void onExecutionReport(BinanceExecutionReportEvent event) {
    try {
      ExecutionIngestionResult result = executionReportIngestionPort.ingest(event.rawPayload());
      meterRegistry
          .counter(
              "worker.connector.ws.execution_report.total",
              "connector",
              CONNECTOR_NAME,
              "outcome",
              result.name().toLowerCase(Locale.ROOT))
          .increment();
      log.info(
          "Binance WS execution report connector={} symbol={} clientOrderId={} executionType={} orderStatus={} ingestOutcome={}",
          CONNECTOR_NAME,
          event.symbol(),
          event.exchangeClientOrderId(),
          event.executionType(),
          event.orderStatus(),
          result);
    } catch (RuntimeException ex) {
      meterRegistry
          .counter(
              "worker.connector.ws.execution_report.total",
              "connector",
              CONNECTOR_NAME,
              "outcome",
              "error")
          .increment();
      onError("INGESTION_ERROR", sanitizeMessage(ex), ex);
    }
  }

  @Override
  public void onError(String errorCode, String errorMessage, Throwable error) {
    Instant now = clock.instant();
    upsertState(
        previous ->
            new ConnectorHealthState(
                previous.connectorName(),
                previous.status(),
                previous.lastSuccessAt(),
                previous.lastPollStartedAt(),
                previous.lastPollCompletedAt(),
                previous.lastErrorAt(),
                previous.lastErrorCode(),
                previous.lastErrorMessage(),
                previous.openOrdersFetched(),
                previous.recentTradesFetched(),
                ConnectorWsConnectionState.DEGRADED,
                previous.lastWsConnectedAt(),
                previous.lastWsDisconnectedAt(),
                now,
                errorCode,
                errorMessage,
                userStreamClient.reconnectAttempts(),
                now));
    log.warn(
        "Binance WS error connector={} errorCode={} message={}",
        CONNECTOR_NAME,
        errorCode,
        errorMessage,
        error);
  }

  private void upsertState(UnaryOperator<ConnectorHealthState> updater) {
    Instant now = clock.instant();
    ConnectorHealthState current =
        healthRepository
            .findByConnectorName(CONNECTOR_NAME)
            .orElse(ConnectorHealthState.initial(CONNECTOR_NAME, now));
    healthRepository.upsert(updater.apply(current));
  }

  private static String maskListenKey(String listenKey) {
    if (listenKey == null || listenKey.length() < 6) {
      return "n/a";
    }
    return listenKey.substring(Math.max(0, listenKey.length() - 6));
  }

  private static String sanitizeMessage(Throwable error) {
    String message = error == null ? null : error.getMessage();
    if (message == null || message.isBlank()) {
      return "execution report ingestion failed";
    }
    return message.length() > 300 ? message.substring(0, 300) : message;
  }
}
