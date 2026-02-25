package com.tradingplatform.integration.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpBinanceOrderClientTest {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-02-25T00:00:00Z"), ZoneOffset.UTC);

  private MockWebServer server;
  private BinanceRequestSigner signer;
  private HttpBinanceOrderClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();

    signer = new BinanceRequestSigner("test-secret", 5000L, FIXED_CLOCK);
    BinanceApiConfig config =
        new BinanceApiConfig(
            server.url("/").uri(),
            "test-api-key",
            "test-secret",
            5000L,
            Duration.ofSeconds(3),
            FIXED_CLOCK);
    client =
        new HttpBinanceOrderClient(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
            new ObjectMapper(),
            config,
            signer);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void shouldSubmitSignedLimitOrderAndMapResponse() throws Exception {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                {
                  "symbol":"BTCUSDT",
                  "orderId":123456789,
                  "clientOrderId":"ord-1001",
                  "status":"NEW"
                }
                """));

    BinanceOrderSubmitResponse response =
        client.submitOrder(
            new BinanceOrderSubmitRequest(
                "BTCUSDT",
                "BUY",
                "LIMIT",
                new BigDecimal("0.01"),
                new BigDecimal("40000.10"),
                "ord-1001"));

    assertNotNull(response);
    assertEquals("123456789", response.exchangeOrderId());
    assertEquals("ord-1001", response.clientOrderId());
    assertEquals("NEW", response.status());

    RecordedRequest recorded = server.takeRequest();
    assertEquals("POST", recorded.getMethod());
    assertEquals("/api/v3/order", recorded.getPath());
    assertEquals("test-api-key", recorded.getHeader("X-MBX-APIKEY"));

    String body = recorded.getBody().readUtf8();
    assertTrue(body.contains("symbol=BTCUSDT"));
    assertTrue(body.contains("side=BUY"));
    assertTrue(body.contains("type=LIMIT"));
    assertTrue(body.contains("quantity=0.01"));
    assertTrue(body.contains("price=40000.10"));
    assertTrue(body.contains("newClientOrderId=ord-1001"));
    assertTrue(body.contains("recvWindow=5000"));
    assertTrue(body.contains("timestamp=1771977600000"));
    assertEquals(expectedSignedPayload("ord-1001"), body);
  }

  @Test
  void shouldMapBinanceErrorPayloadToConnectorException() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(400)
            .setBody(
                """
                {
                  "code": -1013,
                  "msg": "Filter failure: LOT_SIZE"
                }
                """));

    BinanceConnectorException ex =
        assertThrows(
            BinanceConnectorException.class,
            () ->
                client.submitOrder(
                    new BinanceOrderSubmitRequest(
                        "BTCUSDT", "BUY", "LIMIT", new BigDecimal("0.01"), new BigDecimal("1"), "ord-2")));

    assertEquals(400, ex.httpStatus());
    assertEquals(-1013, ex.binanceCode());
    assertTrue(ex.getMessage().contains("LOT_SIZE"));
  }

  @Test
  void shouldFetchOpenOrdersWithSignedQuery() throws Exception {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                [
                  {
                    "symbol":"BTCUSDT",
                    "orderId":987654321,
                    "clientOrderId":"ord-2001",
                    "status":"PARTIALLY_FILLED"
                  }
                ]
                """));

    List<BinanceOpenOrderSnapshot> snapshots = client.fetchOpenOrders();

    assertEquals(1, snapshots.size());
    assertEquals("BTCUSDT", snapshots.get(0).symbol());
    assertEquals("987654321", snapshots.get(0).exchangeOrderId());
    assertEquals("ord-2001", snapshots.get(0).clientOrderId());
    assertEquals("PARTIALLY_FILLED", snapshots.get(0).status());

    RecordedRequest recorded = server.takeRequest();
    assertEquals("GET", recorded.getMethod());
    assertNotNull(recorded.getPath());
    assertTrue(recorded.getPath().startsWith("/api/v3/openOrders?"));
    assertEquals("test-api-key", recorded.getHeader("X-MBX-APIKEY"));
  }

  @Test
  void shouldFetchRecentTradesWithStartTime() throws Exception {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                [
                  {
                    "id":111,
                    "orderId":222,
                    "symbol":"BTCUSDT",
                    "isBuyer":true,
                    "time":1771977601000
                  }
                ]
                """));

    List<BinanceTradeSnapshot> trades =
        client.fetchRecentTrades("BTCUSDT", Instant.parse("2026-02-24T23:30:00Z"));

    assertEquals(1, trades.size());
    BinanceTradeSnapshot trade = trades.get(0);
    assertEquals("BTCUSDT", trade.symbol());
    assertEquals("111", trade.tradeId());
    assertEquals("222", trade.exchangeOrderId());
    assertEquals("BUY", trade.side());
    assertEquals(Instant.parse("2026-02-25T00:00:01Z"), trade.tradeTime());

    RecordedRequest recorded = server.takeRequest();
    assertEquals("GET", recorded.getMethod());
    assertNotNull(recorded.getPath());
    assertTrue(recorded.getPath().startsWith("/api/v3/myTrades?"));
    assertTrue(recorded.getPath().contains("symbol=BTCUSDT"));
    assertTrue(recorded.getPath().contains("startTime=1771975800000"));
  }

  private String expectedSignedPayload(String clientOrderId) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("symbol", "BTCUSDT");
    params.put("side", "BUY");
    params.put("type", "LIMIT");
    params.put("quantity", "0.01");
    params.put("price", "40000.10");
    params.put("newClientOrderId", clientOrderId);
    return signer.sign(params).signedQuery();
  }
}
