package com.tradingplatform.integration.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpBinanceUserStreamClient implements BinanceUserStreamClient {
  private static final Logger log = LoggerFactory.getLogger(HttpBinanceUserStreamClient.class);

  private static final String CONNECTOR_TAG_VALUE = "binance-spot";
  private static final String USER_DATA_STREAM_PATH = "/api/v3/userDataStream";
  private static final String IO_ERROR_CODE = "IO_ERROR";
  private static final String PARSE_ERROR_CODE = "PARSE_ERROR";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final BinanceApiConfig apiConfig;
  private final BinanceUserStreamConfig streamConfig;
  private final MeterRegistry meterRegistry;
  private final ScheduledExecutorService scheduler;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean connected = new AtomicBoolean(false);
  private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
  private final AtomicLong reconnectAttempts = new AtomicLong(0L);
  private final AtomicReference<BinanceUserStreamEventHandler> eventHandler =
      new AtomicReference<>(BinanceUserStreamEventHandler.noop());
  private final AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();
  private final AtomicReference<String> listenKeyRef = new AtomicReference<>();
  private final AtomicReference<ScheduledFuture<?>> reconnectTaskRef = new AtomicReference<>();
  private final AtomicReference<ScheduledFuture<?>> keepAliveTaskRef = new AtomicReference<>();
  private final AtomicReference<ScheduledFuture<?>> stableResetTaskRef = new AtomicReference<>();
  private final AtomicInteger connectionStateGauge = new AtomicInteger(0);
  private final AtomicReference<Instant> connectedAt = new AtomicReference<>();

  public HttpBinanceUserStreamClient(
      HttpClient httpClient,
      ObjectMapper objectMapper,
      BinanceApiConfig apiConfig,
      BinanceUserStreamConfig streamConfig,
      MeterRegistry meterRegistry) {
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
    this.apiConfig = Objects.requireNonNull(apiConfig, "apiConfig is required");
    this.streamConfig = Objects.requireNonNull(streamConfig, "streamConfig is required");
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry is required");
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
              @Override
              public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "binance-user-stream");
                thread.setDaemon(true);
                return thread;
              }
            });
    meterRegistry.gauge(
        "worker.connector.ws.connection.state",
        java.util.List.of(io.micrometer.core.instrument.Tag.of("connector", CONNECTOR_TAG_VALUE)),
        connectionStateGauge);
  }

  @Override
  public void start(BinanceUserStreamEventHandler eventHandler) {
    BinanceUserStreamEventHandler handler =
        eventHandler == null ? BinanceUserStreamEventHandler.noop() : eventHandler;
    this.eventHandler.set(handler);
    if (!running.compareAndSet(false, true)) {
      return;
    }
    scheduleReconnect(Duration.ZERO);
  }

  @Override
  public void stop() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    cancelTask(reconnectTaskRef);
    cancelTask(keepAliveTaskRef);
    cancelTask(stableResetTaskRef);
    WebSocket webSocket = webSocketRef.getAndSet(null);
    if (webSocket != null) {
      try {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "stopped").join();
      } catch (Exception ex) {
        webSocket.abort();
      }
    }
    String listenKey = listenKeyRef.getAndSet(null);
    if (listenKey != null && !listenKey.isBlank()) {
      deleteListenKeyQuietly(listenKey);
    }
    updateConnected(false);
    scheduler.shutdownNow();
  }

  @Override
  public boolean isConnected() {
    return connected.get();
  }

  @Override
  public long reconnectAttempts() {
    return reconnectAttempts.get();
  }

  private void connect() {
    if (!running.get()) {
      return;
    }
    try {
      String listenKey = createListenKey();
      listenKeyRef.set(listenKey);
      URI wsUri = resolveWsUri(listenKey);
      log.info("Connecting Binance user stream ws_uri={}", wsUri);
      httpClient
          .newWebSocketBuilder()
          .connectTimeout(apiConfig.timeout())
          .buildAsync(wsUri, new Listener(listenKey))
          .join();
    } catch (Exception ex) {
      handleReconnectFailure(ex);
    }
  }

  private void handleReconnectFailure(Throwable error) {
    updateConnected(false);
    String code = errorCode(error);
    BinanceUserStreamEventHandler handler = eventHandler.get();
    handler.onError(code, sanitizeMessage(error), unwrapCompletionException(error));
    meterRegistry
        .counter("worker.connector.ws.errors.total", "connector", CONNECTOR_TAG_VALUE, "error", code)
        .increment();
    int attempt = consecutiveFailures.incrementAndGet();
    long reconnectCount = reconnectAttempts.incrementAndGet();
    Duration delay = nextBackoff(attempt);
    meterRegistry
        .counter("worker.connector.ws.reconnect.total", "connector", CONNECTOR_TAG_VALUE)
        .increment();
    handler.onReconnectScheduled(reconnectCount, delay);
    scheduleReconnect(delay);
  }

  private void scheduleReconnect(Duration delay) {
    if (!running.get()) {
      return;
    }
    cancelTask(reconnectTaskRef);
    long delayMs = Math.max(0L, delay.toMillis());
    ScheduledFuture<?> future = scheduler.schedule(this::connect, delayMs, TimeUnit.MILLISECONDS);
    reconnectTaskRef.set(future);
  }

  private void scheduleKeepAlive(String listenKey) {
    cancelTask(keepAliveTaskRef);
    long intervalMs = streamConfig.keepAliveInterval().toMillis();
    ScheduledFuture<?> future =
        scheduler.scheduleAtFixedRate(
            () -> {
              if (!running.get() || !connected.get()) {
                return;
              }
              try {
                keepAliveListenKey(listenKey);
              } catch (Exception ex) {
                log.warn("Listen key keepalive failed, forcing reconnect", ex);
                WebSocket socket = webSocketRef.getAndSet(null);
                if (socket != null) {
                  socket.abort();
                }
              }
            },
            intervalMs,
            intervalMs,
            TimeUnit.MILLISECONDS);
    keepAliveTaskRef.set(future);
  }

  private void scheduleStableReset() {
    cancelTask(stableResetTaskRef);
    long delayMs = streamConfig.stableConnectionReset().toMillis();
    ScheduledFuture<?> future =
        scheduler.schedule(
            () -> {
              if (running.get() && connected.get()) {
                consecutiveFailures.set(0);
              }
            },
            delayMs,
            TimeUnit.MILLISECONDS);
    stableResetTaskRef.set(future);
  }

  private String createListenKey() {
    HttpRequest request =
        HttpRequest.newBuilder(resolveRestUri(USER_DATA_STREAM_PATH))
            .timeout(apiConfig.timeout())
            .header("X-MBX-APIKEY", apiConfig.apiKey())
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    HttpResponse<String> response = send(request, "create_listen_key");
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw parseFailure(response.statusCode(), response.body());
    }
    JsonNode root = parseJson(response.body());
    if (!root.hasNonNull("listenKey")) {
      throw new IllegalStateException("Binance response missing listenKey");
    }
    String listenKey = root.get("listenKey").asText();
    if (listenKey == null || listenKey.isBlank()) {
      throw new IllegalStateException("Binance response contains blank listenKey");
    }
    return listenKey;
  }

  private void keepAliveListenKey(String listenKey) {
    String encoded = URLEncoder.encode(listenKey, StandardCharsets.UTF_8);
    HttpRequest request =
        HttpRequest.newBuilder(resolveRestUri(USER_DATA_STREAM_PATH + "?listenKey=" + encoded))
            .timeout(apiConfig.timeout())
            .header("X-MBX-APIKEY", apiConfig.apiKey())
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build();
    HttpResponse<String> response = send(request, "keepalive_listen_key");
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw parseFailure(response.statusCode(), response.body());
    }
  }

  private void deleteListenKeyQuietly(String listenKey) {
    try {
      String encoded = URLEncoder.encode(listenKey, StandardCharsets.UTF_8);
      HttpRequest request =
          HttpRequest.newBuilder(resolveRestUri(USER_DATA_STREAM_PATH + "?listenKey=" + encoded))
              .timeout(apiConfig.timeout())
              .header("X-MBX-APIKEY", apiConfig.apiKey())
              .method("DELETE", HttpRequest.BodyPublishers.noBody())
              .build();
      send(request, "delete_listen_key");
    } catch (Exception ex) {
      log.debug("Failed to delete listen key on shutdown", ex);
    }
  }

  private HttpResponse<String> send(HttpRequest request, String action) {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new BinanceConnectorException(
          "Binance " + action + " request was interrupted", -1, null, ex);
    } catch (IOException ex) {
      throw new BinanceConnectorException("Failed Binance " + action + " request", -1, null, ex);
    }
  }

  private URI resolveRestUri(String path) {
    return apiConfig.baseUri().resolve(path);
  }

  private URI resolveWsUri(String listenKey) {
    String base = streamConfig.wsBaseUri().toString();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return URI.create(base + "/ws/" + listenKey);
  }

  private Duration nextBackoff(int attempt) {
    long base = streamConfig.reconnectBaseBackoff().toMillis();
    long max = streamConfig.reconnectMaxBackoff().toMillis();
    long value = base;
    int steps = Math.max(0, attempt - 1);
    for (int i = 0; i < steps; i++) {
      if (value >= max / 2) {
        value = max;
        break;
      }
      value *= 2;
    }
    return Duration.ofMillis(Math.min(value, max));
  }

  private JsonNode parseJson(String body) {
    try {
      return objectMapper.readTree(body);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to parse Binance response JSON: " + body, ex);
    }
  }

  private BinanceConnectorException parseFailure(int statusCode, String responseBody) {
    try {
      JsonNode node = parseJson(responseBody);
      Integer code = node.hasNonNull("code") ? node.get("code").asInt() : null;
      String message = node.hasNonNull("msg") ? node.get("msg").asText() : "Unknown Binance error";
      return new BinanceConnectorException(
          "Binance user stream error status="
              + statusCode
              + " code="
              + (code == null ? "null" : code)
              + " message="
              + message,
          statusCode,
          code);
    } catch (RuntimeException ex) {
      return new BinanceConnectorException(
          "Binance user stream error status=" + statusCode + " body=" + responseBody,
          statusCode,
          null,
          ex);
    }
  }

  static BinanceExecutionReportEvent parseExecutionReport(String payload, ObjectMapper objectMapper) {
    try {
      JsonNode root = objectMapper.readTree(payload);
      String tradeIdRaw = root.path("t").asText("");
      String tradeId = "-1".equals(tradeIdRaw) ? null : blankToNull(tradeIdRaw);
      return new BinanceExecutionReportEvent(
          root.path("s").asText(""),
          root.path("i").asText(""),
          root.path("c").asText(""),
          tradeId,
          root.path("S").asText(""),
          root.path("x").asText(""),
          root.path("X").asText(""),
          parseDecimal(root.path("z").asText("0")),
          parseDecimal(root.path("l").asText("0")),
          parseDecimal(root.path("L").asText("0")),
          parseDecimal(root.path("q").asText("0")),
          parseDecimal(root.path("p").asText("0")),
          parseDecimal(root.path("n").asText("0")),
          blankToNull(root.path("N").asText("")),
          parseEpochMillis(root.path("E").asLong(0L)),
          parseEpochMillis(root.path("T").asLong(0L)),
          payload);
    } catch (IOException ex) {
      throw new IllegalArgumentException("Invalid executionReport payload", ex);
    }
  }

  private static void cancelTask(AtomicReference<ScheduledFuture<?>> taskRef) {
    ScheduledFuture<?> task = taskRef.getAndSet(null);
    if (task != null) {
      task.cancel(true);
    }
  }

  private void updateConnected(boolean value) {
    connected.set(value);
    connectionStateGauge.set(value ? 1 : 0);
    if (value) {
      connectedAt.set(Instant.now(apiConfig.clock()));
    }
  }

  private static String errorCode(Throwable error) {
    Throwable unwrapped = unwrapCompletionException(error);
    String simpleName = unwrapped.getClass().getSimpleName();
    if (unwrapped instanceof BinanceConnectorException connectorEx
        && connectorEx.httpStatus() > 0) {
      return "HTTP_" + connectorEx.httpStatus();
    }
    return simpleName == null || simpleName.isBlank() ? IO_ERROR_CODE : simpleName;
  }

  private static Throwable unwrapCompletionException(Throwable throwable) {
    if (throwable instanceof java.util.concurrent.CompletionException completionException
        && completionException.getCause() != null) {
      return completionException.getCause();
    }
    return throwable;
  }

  private static String sanitizeMessage(Throwable error) {
    Throwable unwrapped = unwrapCompletionException(error);
    String message = unwrapped.getMessage();
    if (message == null || message.isBlank()) {
      return unwrapped.getClass().getSimpleName();
    }
    String compact = message.replaceAll("\\s+", " ").trim();
    if (compact.length() <= 300) {
      return compact;
    }
    return compact.substring(0, 300);
  }

  private static BigDecimal parseDecimal(String value) {
    if (value == null || value.isBlank()) {
      return BigDecimal.ZERO;
    }
    return new BigDecimal(value);
  }

  private static Instant parseEpochMillis(long epochMillis) {
    if (epochMillis <= 0) {
      return null;
    }
    return Instant.ofEpochMilli(epochMillis);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private final class Listener implements WebSocket.Listener {
    private final String listenKey;
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final StringBuilder frameBuffer = new StringBuilder();

    private Listener(String listenKey) {
      this.listenKey = listenKey;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocketRef.set(webSocket);
      updateConnected(true);
      scheduleKeepAlive(listenKey);
      scheduleStableReset();
      eventHandler.get().onConnected(listenKey);
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      frameBuffer.append(data);
      if (last) {
        handleMessage(frameBuffer.toString());
        frameBuffer.setLength(0);
      }
      webSocket.request(1);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      terminate(statusCode, reason, null);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      terminate(1011, "ws_error", error);
    }

    private void handleMessage(String payload) {
      try {
        JsonNode root = objectMapper.readTree(payload);
        String eventType = root.path("e").asText("");
        if (!"executionReport".equals(eventType)) {
          meterRegistry
              .counter(
                  "worker.connector.ws.messages.total",
                  "connector",
                  CONNECTOR_TAG_VALUE,
                  "type",
                  "ignored")
              .increment();
          return;
        }
        BinanceExecutionReportEvent executionReport = parseExecutionReport(payload, objectMapper);
        meterRegistry
            .counter(
                "worker.connector.ws.messages.total",
                "connector",
                CONNECTOR_TAG_VALUE,
                "type",
                "execution_report")
            .increment();
        eventHandler.get().onExecutionReport(executionReport);
      } catch (Exception ex) {
        meterRegistry
            .counter(
                "worker.connector.ws.messages.total",
                "connector",
                CONNECTOR_TAG_VALUE,
                "type",
                "parse_error")
            .increment();
        eventHandler.get().onError(PARSE_ERROR_CODE, sanitizeMessage(ex), ex);
      }
    }

    private void terminate(int statusCode, String reason, Throwable error) {
      if (!terminated.compareAndSet(false, true)) {
        return;
      }
      cancelTask(keepAliveTaskRef);
      cancelTask(stableResetTaskRef);
      updateConnected(false);
      WebSocket socket = webSocketRef.getAndSet(null);
      if (socket != null) {
        socket.abort();
      }
      String currentListenKey = listenKeyRef.getAndSet(null);
      if (currentListenKey != null && !currentListenKey.isBlank()) {
        deleteListenKeyQuietly(currentListenKey);
      }
      Duration connectedFor = durationSinceConnected();
      if (connectedFor.compareTo(streamConfig.stableConnectionReset()) >= 0) {
        consecutiveFailures.set(0);
      }
      eventHandler.get().onDisconnected(statusCode, reason == null ? "" : reason);
      if (error != null) {
        String code = errorCode(error);
        meterRegistry
            .counter("worker.connector.ws.errors.total", "connector", CONNECTOR_TAG_VALUE, "error", code)
            .increment();
        eventHandler.get().onError(code, sanitizeMessage(error), unwrapCompletionException(error));
      }
      if (!running.get()) {
        return;
      }
      int attempt = consecutiveFailures.incrementAndGet();
      long reconnectCount = reconnectAttempts.incrementAndGet();
      Duration delay = nextBackoff(attempt);
      meterRegistry
          .counter("worker.connector.ws.reconnect.total", "connector", CONNECTOR_TAG_VALUE)
          .increment();
      eventHandler.get().onReconnectScheduled(reconnectCount, delay);
      scheduleReconnect(delay);
    }
  }

  private Duration durationSinceConnected() {
    Instant connectedInstant = connectedAt.get();
    if (connectedInstant == null) {
      return Duration.ZERO;
    }
    return Duration.between(connectedInstant, Instant.now(apiConfig.clock())).abs();
  }
}
