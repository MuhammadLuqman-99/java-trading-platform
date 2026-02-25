package com.tradingplatform.integration.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.orders.OrderStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

public class RestBinanceOrderGateway implements BinanceOrderGateway {
  private static final String ORDER_PATH = "/api/v3/order";
  private static final String API_KEY_HEADER = "X-MBX-APIKEY";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final BinanceConnectorProperties properties;
  private final ExchangeOrderStatusMapper statusMapper;
  private final BinanceRequestSigner requestSigner;
  private final RateLimitRetryExecutor retryExecutor;

  public RestBinanceOrderGateway(
      RestClient restClient,
      ObjectMapper objectMapper,
      BinanceConnectorProperties properties,
      ExchangeOrderStatusMapper statusMapper,
      BinanceRequestSigner requestSigner,
      RateLimitRetryExecutor retryExecutor) {
    this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.statusMapper = Objects.requireNonNull(statusMapper, "statusMapper must not be null");
    this.requestSigner = Objects.requireNonNull(requestSigner, "requestSigner must not be null");
    this.retryExecutor = Objects.requireNonNull(retryExecutor, "retryExecutor must not be null");
  }

  @Override
  public BinanceCancelOrderResult cancelOrder(BinanceCancelOrderRequest request) {
    validateOrderIdentity(request.exchangeOrderId(), request.clientOrderId());
    validateSymbol(request.symbol());

    return retryExecutor.execute(
        () -> {
          Map<String, String> params = new LinkedHashMap<>();
          params.put("symbol", request.symbol());
          if (request.exchangeOrderId() != null) {
            params.put("orderId", String.valueOf(request.exchangeOrderId()));
          }
          if (hasText(request.clientOrderId())) {
            params.put("origClientOrderId", request.clientOrderId());
          }
          String body = executeSignedOrderCall(HttpMethod.DELETE, params);
          JsonNode root = parseJson(body);
          String externalStatus = requiredText(root, "status");
          OrderStatus domainStatus =
              statusMapper.toDomainStatus(properties.getStatusMapping().getVenue(), externalStatus);
          return new BinanceCancelOrderResult(
              requiredText(root, "symbol"),
              optionalText(root, "orderId"),
              optionalText(root, "clientOrderId"),
              externalStatus,
              domainStatus,
              optionalDecimal(root, "executedQty"),
              body);
        });
  }

  @Override
  public BinanceQueryOrderResult queryOrder(BinanceQueryOrderRequest request) {
    validateOrderIdentity(request.exchangeOrderId(), request.clientOrderId());
    validateSymbol(request.symbol());

    return retryExecutor.execute(
        () -> {
          Map<String, String> params = new LinkedHashMap<>();
          params.put("symbol", request.symbol());
          if (request.exchangeOrderId() != null) {
            params.put("orderId", String.valueOf(request.exchangeOrderId()));
          }
          if (hasText(request.clientOrderId())) {
            params.put("origClientOrderId", request.clientOrderId());
          }
          String body = executeSignedOrderCall(HttpMethod.GET, params);
          JsonNode root = parseJson(body);
          String externalStatus = requiredText(root, "status");
          OrderStatus domainStatus =
              statusMapper.toDomainStatus(properties.getStatusMapping().getVenue(), externalStatus);
          return new BinanceQueryOrderResult(
              requiredText(root, "symbol"),
              optionalText(root, "orderId"),
              optionalText(root, "clientOrderId"),
              externalStatus,
              domainStatus,
              optionalDecimal(root, "origQty"),
              optionalDecimal(root, "executedQty"),
              body);
        });
  }

  private String executeSignedOrderCall(HttpMethod method, Map<String, String> params) {
    BinanceRequestSigner.SignedRequest signed = requestSigner.sign(params);
    ResponseEntity<String> response =
        restClient
            .method(method)
            .uri(ORDER_PATH + "?" + signed.signedQuery())
            .header(API_KEY_HEADER, properties.getApiKey())
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, result) -> raiseBinanceApiException(result))
            .toEntity(String.class);
    return response.getBody() == null ? "{}" : response.getBody();
  }

  private void raiseBinanceApiException(org.springframework.http.client.ClientHttpResponse response)
      throws IOException {
    String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
    Integer errorCode = extractBinanceErrorCode(body);
    throw new BinanceApiException(
        response.getStatusCode().value(), response.getHeaders(), body, errorCode);
  }

  private Integer extractBinanceErrorCode(String body) {
    try {
      JsonNode root = objectMapper.readTree(body);
      if (!root.hasNonNull("code")) {
        return null;
      }
      return root.get("code").isInt() ? root.get("code").asInt() : null;
    } catch (IOException ignored) {
      return null;
    }
  }

  private JsonNode parseJson(String body) {
    try {
      return objectMapper.readTree(body);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to parse Binance response JSON", ex);
    }
  }

  private static String requiredText(JsonNode root, String field) {
    String value = optionalText(root, field);
    if (!hasText(value)) {
      throw new IllegalStateException("Missing required field in Binance response: " + field);
    }
    return value;
  }

  private static String optionalText(JsonNode root, String field) {
    JsonNode node = root.get(field);
    if (node == null || node.isNull()) {
      return null;
    }
    return node.asText();
  }

  private static BigDecimal optionalDecimal(JsonNode root, String field) {
    String text = optionalText(root, field);
    if (!hasText(text)) {
      return null;
    }
    return new BigDecimal(text);
  }

  private static void validateOrderIdentity(Long exchangeOrderId, String clientOrderId) {
    if (exchangeOrderId == null && !hasText(clientOrderId)) {
      throw new IllegalArgumentException("Either exchangeOrderId or clientOrderId is required");
    }
  }

  private static void validateSymbol(String symbol) {
    if (!hasText(symbol)) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
