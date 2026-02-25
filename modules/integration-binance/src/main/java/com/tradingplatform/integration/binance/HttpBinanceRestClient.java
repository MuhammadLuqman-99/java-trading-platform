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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpBinanceRestClient implements BinanceRestClient {
  private static final String SERVER_TIME_PATH = "/api/v3/time";
  private static final String ACCOUNT_PATH = "/api/v3/account";
  private static final int IO_FAILURE_STATUS = -1;

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final BinanceApiConfig config;
  private final BinanceRequestSigner signer;

  public HttpBinanceRestClient(
      HttpClient httpClient,
      ObjectMapper objectMapper,
      BinanceApiConfig config,
      BinanceRequestSigner signer) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.config = config;
    this.signer = signer;
  }

  @Override
  public long getServerTime() {
    HttpRequest request =
        HttpRequest.newBuilder(resolve(SERVER_TIME_PATH)).timeout(config.timeout()).GET().build();
    HttpResponse<String> response = execute(request, "server time");
    JsonNode node = parseJson(response.body());
    if (!node.hasNonNull("serverTime")) {
      throw new IllegalStateException("Binance response missing field: serverTime");
    }
    return node.get("serverTime").asLong();
  }

  @Override
  public BinanceAccountInfo getAccountInfo() {
    Map<String, String> params = new LinkedHashMap<>();
    URI uri = resolve(ACCOUNT_PATH + "?" + signer.sign(params).signedQuery());

    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(config.timeout())
            .header("X-MBX-APIKEY", config.apiKey())
            .GET()
            .build();

    HttpResponse<String> response = execute(request, "account");
    return parseAccountInfo(response.body());
  }

  private HttpResponse<String> execute(HttpRequest request, String action) {
    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new BinanceConnectorException(
          "Binance " + action + " request was interrupted", IO_FAILURE_STATUS, null, ex);
    } catch (IOException ex) {
      throw new BinanceConnectorException(
          "Failed to call Binance " + action + " endpoint", IO_FAILURE_STATUS, null, ex);
    }

    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return response;
    }
    throw parseFailure(response.statusCode(), response.body());
  }

  private BinanceAccountInfo parseAccountInfo(String responseBody) {
    JsonNode node = parseJson(responseBody);
    boolean canTrade = node.path("canTrade").asBoolean(false);
    String accountType = node.path("accountType").asText("");
    if (accountType.isBlank()) {
      accountType = "UNKNOWN";
    }

    List<BinanceAccountBalance> balances = new ArrayList<>();
    JsonNode balancesNode = node.path("balances");
    if (balancesNode.isArray()) {
      for (JsonNode balanceNode : balancesNode) {
        String asset = balanceNode.path("asset").asText("");
        if (asset.isBlank()) {
          continue;
        }
        balances.add(
            new BinanceAccountBalance(
                asset,
                parseDecimal(balanceNode.path("free").asText("0")),
                parseDecimal(balanceNode.path("locked").asText("0"))));
      }
    }

    return new BinanceAccountInfo(canTrade, accountType, List.copyOf(balances));
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

  private static BigDecimal parseDecimal(String value) {
    if (value == null || value.isBlank()) {
      return BigDecimal.ZERO;
    }
    return new BigDecimal(value);
  }
}
