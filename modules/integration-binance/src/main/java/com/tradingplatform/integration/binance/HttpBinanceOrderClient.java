package com.tradingplatform.integration.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class HttpBinanceOrderClient implements BinanceOrderClient, BinancePollingClient {
  private static final String NEW_ORDER_PATH = "/api/v3/order";
  private static final String OPEN_ORDERS_PATH = "/api/v3/openOrders";
  private static final String MY_TRADES_PATH = "/api/v3/myTrades";
  private static final int IO_FAILURE_STATUS = -1;

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final BinanceApiConfig config;
  private final BinanceRequestSigner signer;
  private final int maxAttempts;
  private final Duration baseBackoff;
  private final Duration maxBackoff;
  private final boolean jitterEnabled;

  public HttpBinanceOrderClient(
      HttpClient httpClient,
      ObjectMapper objectMapper,
      BinanceApiConfig config,
      BinanceRequestSigner signer) {
    this(httpClient, objectMapper, config, signer, 3, Duration.ofMillis(200), Duration.ofSeconds(5), true);
  }

  public HttpBinanceOrderClient(
      HttpClient httpClient,
      ObjectMapper objectMapper,
      BinanceApiConfig config,
      BinanceRequestSigner signer,
      int maxAttempts,
      Duration baseBackoff,
      Duration maxBackoff,
      boolean jitterEnabled) {
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
    this.config = Objects.requireNonNull(config, "config is required");
    this.signer = Objects.requireNonNull(signer, "signer is required");
    this.maxAttempts = Math.max(1, maxAttempts);
    this.baseBackoff =
        baseBackoff == null || baseBackoff.isNegative() ? Duration.ZERO : baseBackoff;
    this.maxBackoff =
        maxBackoff == null || maxBackoff.isNegative() ? Duration.ZERO : maxBackoff;
    this.jitterEnabled = jitterEnabled;
  }

  @Override
  public BinanceOrderSubmitResponse submitOrder(BinanceOrderSubmitRequest request) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("symbol", request.symbol());
    params.put("side", request.side());
    params.put("type", request.type());
    params.put("quantity", toApiDecimal(request.quantity()));
    if (request.price() != null) {
      params.put("price", toApiDecimal(request.price()));
    }
    params.put("newClientOrderId", request.newClientOrderId());
    String payload = signer.sign(params).signedQuery();

    HttpRequest httpRequest =
        HttpRequest.newBuilder(resolve(NEW_ORDER_PATH))
            .timeout(config.timeout())
            .header("X-MBX-APIKEY", config.apiKey())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();
    HttpResponse<String> response = sendWithRetry(httpRequest, "submit_order");
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return parseSuccess(response.body());
    }
    throw parseFailure(response.statusCode(), response.body());
  }

  @Override
  public List<BinanceOpenOrderSnapshot> fetchOpenOrders() {
    Map<String, String> params = signedQueryDefaults();
    HttpRequest request = signedGet(OPEN_ORDERS_PATH, params);
    HttpResponse<String> response = sendWithRetry(request, "open_orders");
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return parseOpenOrders(response.body());
    }
    throw parseFailure(response.statusCode(), response.body());
  }

  @Override
  public List<BinanceTradeSnapshot> fetchRecentTrades(String symbol, Instant fromInclusive) {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol is required");
    }
    if (fromInclusive == null) {
      throw new IllegalArgumentException("fromInclusive is required");
    }
    Map<String, String> params = signedQueryDefaults();
    params.put("symbol", symbol);
    params.put("startTime", Long.toString(fromInclusive.toEpochMilli()));
    HttpRequest request = signedGet(MY_TRADES_PATH, params);
    HttpResponse<String> response = sendWithRetry(request, "recent_trades");
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return parseRecentTrades(response.body(), symbol);
    }
    throw parseFailure(response.statusCode(), response.body());
  }

  private HttpRequest signedGet(String path, Map<String, String> params) {
    String query = signer.sign(params).signedQuery();
    return HttpRequest.newBuilder(resolve(path + "?" + query))
        .timeout(config.timeout())
        .header("X-MBX-APIKEY", config.apiKey())
        .GET()
        .build();
  }

  private Map<String, String> signedQueryDefaults() {
    return new LinkedHashMap<>();
  }

  private HttpResponse<String> sendWithRetry(HttpRequest request, String operationName) {
    int attempt = 0;
    while (true) {
      attempt++;
      try {
        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (isRetryableStatus(response.statusCode()) && attempt < maxAttempts) {
          sleepBackoff(attempt, operationName);
          continue;
        }
        return response;
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new BinanceConnectorException(
            "Binance " + operationName + " request was interrupted", IO_FAILURE_STATUS, null, ex);
      } catch (IOException ex) {
        if (attempt >= maxAttempts) {
          throw new BinanceConnectorException(
              "Failed to call Binance " + operationName + " endpoint", IO_FAILURE_STATUS, null, ex);
        }
        sleepBackoff(attempt, operationName);
      }
    }
  }

  private void sleepBackoff(int attempt, String operationName) {
    long delayMs = computeDelayMs(attempt);
    if (delayMs <= 0L) {
      return;
    }
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new BinanceConnectorException(
          "Backoff interrupted for Binance " + operationName, IO_FAILURE_STATUS, null, ex);
    }
  }

  private long computeDelayMs(int attempt) {
    long base = Math.max(0L, baseBackoff.toMillis());
    if (base == 0L) {
      return 0L;
    }
    long cap = Math.max(base, maxBackoff.toMillis());
    int exponent = Math.max(0, attempt - 1);
    long exponential = base;
    for (int i = 0; i < exponent; i++) {
      if (exponential >= cap / 2) {
        exponential = cap;
        break;
      }
      exponential = exponential * 2;
    }
    long clamped = Math.min(cap, exponential);
    if (!jitterEnabled || clamped <= 1L) {
      return clamped;
    }
    long jitterRange = Math.max(1L, clamped / 4L);
    long jitter = ThreadLocalRandom.current().nextLong(jitterRange + 1L);
    return Math.min(cap, clamped + jitter);
  }

  private static boolean isRetryableStatus(int statusCode) {
    return statusCode == 429 || statusCode >= 500;
  }

  private List<BinanceOpenOrderSnapshot> parseOpenOrders(String responseBody) {
    JsonNode root = parseJson(responseBody);
    if (!root.isArray()) {
      throw new IllegalStateException("Expected open orders array response from Binance");
    }
    List<BinanceOpenOrderSnapshot> snapshots = new ArrayList<>();
    for (JsonNode node : root) {
      snapshots.add(
          new BinanceOpenOrderSnapshot(
              textValue(node, "symbol"),
              textValue(node, "clientOrderId"),
              textValue(node, "orderId"),
              textValue(node, "status")));
    }
    return snapshots;
  }

  private List<BinanceTradeSnapshot> parseRecentTrades(String responseBody, String fallbackSymbol) {
    JsonNode root = parseJson(responseBody);
    if (!root.isArray()) {
      throw new IllegalStateException("Expected trades array response from Binance");
    }
    List<BinanceTradeSnapshot> snapshots = new ArrayList<>();
    for (JsonNode node : root) {
      String symbol = node.hasNonNull("symbol") ? node.get("symbol").asText() : fallbackSymbol;
      String tradeId = textValue(node, "id");
      String orderId = textValue(node, "orderId");
      String side = node.path("isBuyer").asBoolean() ? "BUY" : "SELL";
      BigDecimal qty = decimalValue(node, "qty");
      BigDecimal price = decimalValue(node, "price");
      String feeAsset = optionalTextValue(node, "commissionAsset");
      BigDecimal feeAmount = optionalDecimalValue(node, "commission");
      Instant tradeTime = Instant.ofEpochMilli(longValue(node, "time"));
      snapshots.add(
          new BinanceTradeSnapshot(
              symbol, tradeId, orderId, side, qty, price, feeAsset, feeAmount, tradeTime));
    }
    return snapshots;
  }

  private BinanceOrderSubmitResponse parseSuccess(String responseBody) {
    JsonNode node = parseJson(responseBody);
    String exchangeOrderId = textValue(node, "orderId");
    String clientOrderId = textValue(node, "clientOrderId");
    String status = textValue(node, "status");
    return new BinanceOrderSubmitResponse(exchangeOrderId, clientOrderId, status);
  }

  private BinanceConnectorException parseFailure(int statusCode, String responseBody) {
    try {
      JsonNode node = parseJson(responseBody);
      Integer code = node.hasNonNull("code") ? node.get("code").intValue() : null;
      String msg = node.hasNonNull("msg") ? node.get("msg").asText() : "Unknown Binance error";
      return new BinanceConnectorException(
          "Binance API error status="
              + statusCode
              + " code="
              + (code == null ? "null" : code)
              + " message="
              + msg,
          statusCode,
          code);
    } catch (RuntimeException ex) {
      return new BinanceConnectorException(
          "Binance API error status=" + statusCode + " body=" + responseBody,
          statusCode,
          null,
          ex);
    }
  }

  private JsonNode parseJson(String responseBody) {
    try {
      return objectMapper.readTree(responseBody);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to parse Binance response JSON: " + responseBody, ex);
    }
  }

  private URI resolve(String path) {
    return config.baseUri().resolve(path);
  }

  private static String toApiDecimal(BigDecimal value) {
    return value.toPlainString();
  }

  private static String textValue(JsonNode node, String field) {
    if (!node.hasNonNull(field)) {
      throw new IllegalStateException("Binance response missing field: " + field);
    }
    String value = node.get(field).asText();
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Binance response has blank field: " + field);
    }
    return value;
  }

  private static long longValue(JsonNode node, String field) {
    if (!node.hasNonNull(field) || !node.get(field).canConvertToLong()) {
      throw new IllegalStateException("Binance response missing numeric field: " + field);
    }
    return node.get(field).asLong();
  }

  private static BigDecimal decimalValue(JsonNode node, String field) {
    if (!node.hasNonNull(field)) {
      throw new IllegalStateException("Binance response missing field: " + field);
    }
    String raw = node.get(field).asText();
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException("Binance response has blank field: " + field);
    }
    try {
      return new BigDecimal(raw);
    } catch (NumberFormatException ex) {
      throw new IllegalStateException("Binance response has invalid decimal field: " + field, ex);
    }
  }

  private static BigDecimal optionalDecimalValue(JsonNode node, String field) {
    if (!node.hasNonNull(field)) {
      return null;
    }
    String raw = node.get(field).asText();
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(raw);
    } catch (NumberFormatException ex) {
      throw new IllegalStateException("Binance response has invalid decimal field: " + field, ex);
    }
  }

  private static String optionalTextValue(JsonNode node, String field) {
    if (!node.hasNonNull(field)) {
      return null;
    }
    String value = node.get(field).asText();
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isBlank() ? null : trimmed;
  }
}
