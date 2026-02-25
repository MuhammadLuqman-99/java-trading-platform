package com.tradingplatform.integration.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.orders.OrderStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestBinanceOrderGatewayTest {
  @Test
  void shouldCancelOrderAndMapDomainStatus() {
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:00:00Z"), ZoneOffset.UTC);
    BinanceConnectorProperties properties = new BinanceConnectorProperties();
    properties.setBaseUrl("https://binance.test");
    properties.setApiKey("api-key");
    properties.setApiSecret("secret");

    RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();

    BinanceRequestSigner signer =
        new BinanceRequestSigner(properties.getApiSecret(), properties.getRecvWindowMs(), clock);
    LinkedHashMap<String, String> params = new LinkedHashMap<>();
    params.put("symbol", "BTCUSDT");
    params.put("orderId", "12345");
    String expectedQuery = signer.sign(params).signedQuery();

    server
        .expect(
            request -> {
              assertEquals("/api/v3/order", request.getURI().getPath());
              assertEquals(expectedQuery, request.getURI().getRawQuery());
            })
        .andExpect(method(HttpMethod.DELETE))
        .andExpect(header("X-MBX-APIKEY", "api-key"))
        .andRespond(
            withSuccess(
                """
                {
                  "symbol":"BTCUSDT",
                  "orderId":12345,
                  "clientOrderId":"abc-1",
                  "status":"CANCELED",
                  "executedQty":"0.001"
                }
                """,
                MediaType.APPLICATION_JSON));

    ExchangeOrderStatusMappingRepository mappingRepository =
        () ->
            List.of(
                new ExchangeOrderStatusMapping(
                    BinanceVenue.BINANCE_SPOT, "CANCELED", OrderStatus.CANCELED, true, false));

    RestBinanceOrderGateway gateway =
        new RestBinanceOrderGateway(
            restClient,
            new ObjectMapper(),
            properties,
            new DatabaseBackedExchangeOrderStatusMapper(mappingRepository, new SimpleMeterRegistry()),
            signer,
            new RateLimitRetryExecutor(
                1,
                new RetryAfterParser(clock),
                new JitteredExponentialBackoff(200L, 5000L, false),
                duration -> {},
                new SimpleMeterRegistry()));

    BinanceCancelOrderResult result =
        gateway.cancelOrder(new BinanceCancelOrderRequest("BTCUSDT", 12345L, null));

    assertEquals(OrderStatus.CANCELED, result.domainStatus());
    assertEquals("CANCELED", result.externalStatus());
    assertEquals("12345", result.exchangeOrderId());
    server.verify();
  }

  @Test
  void shouldQueryOrderAndMapDomainStatus() {
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:00:00Z"), ZoneOffset.UTC);
    BinanceConnectorProperties properties = new BinanceConnectorProperties();
    properties.setBaseUrl("https://binance.test");
    properties.setApiKey("api-key");
    properties.setApiSecret("secret");

    RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();

    BinanceRequestSigner signer =
        new BinanceRequestSigner(properties.getApiSecret(), properties.getRecvWindowMs(), clock);

    server
        .expect(
            request -> {
              assertEquals("/api/v3/order", request.getURI().getPath());
              String query = request.getURI().getRawQuery();
              assertTrue(query.contains("symbol=ETHUSDT"));
              assertTrue(query.contains("origClientOrderId=cli-123"));
              assertTrue(query.contains("signature="));
            })
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("X-MBX-APIKEY", "api-key"))
        .andRespond(
            withSuccess(
                """
                {
                  "symbol":"ETHUSDT",
                  "orderId":998877,
                  "clientOrderId":"cli-123",
                  "status":"NEW",
                  "origQty":"2.5",
                  "executedQty":"0.0"
                }
                """,
                MediaType.APPLICATION_JSON));

    ExchangeOrderStatusMappingRepository mappingRepository =
        () ->
            List.of(
                new ExchangeOrderStatusMapping(
                    BinanceVenue.BINANCE_SPOT, "NEW", OrderStatus.NEW, false, true));

    RestBinanceOrderGateway gateway =
        new RestBinanceOrderGateway(
            restClient,
            new ObjectMapper(),
            properties,
            new DatabaseBackedExchangeOrderStatusMapper(mappingRepository, new SimpleMeterRegistry()),
            signer,
            new RateLimitRetryExecutor(
                1,
                new RetryAfterParser(clock),
                new JitteredExponentialBackoff(200L, 5000L, false),
                duration -> {},
                new SimpleMeterRegistry()));

    BinanceQueryOrderResult result =
        gateway.queryOrder(new BinanceQueryOrderRequest("ETHUSDT", null, "cli-123"));

    assertEquals(OrderStatus.NEW, result.domainStatus());
    assertEquals("NEW", result.externalStatus());
    assertEquals("998877", result.exchangeOrderId());
    server.verify();
  }
}
