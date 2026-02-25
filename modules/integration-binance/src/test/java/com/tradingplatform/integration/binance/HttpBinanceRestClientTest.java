package com.tradingplatform.integration.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpBinanceRestClientTest {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-02-25T00:00:00Z"), ZoneOffset.UTC);

  private MockWebServer server;
  private BinanceRequestSigner signer;
  private HttpBinanceRestClient client;

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
        new HttpBinanceRestClient(
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
  void shouldCallPublicServerTimeEndpoint() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"serverTime\":1771977600123}"));

    long serverTime = client.getServerTime();

    assertEquals(1771977600123L, serverTime);
    RecordedRequest recorded = server.takeRequest();
    assertEquals("GET", recorded.getMethod());
    assertEquals("/api/v3/time", recorded.getPath());
    assertNull(recorded.getHeader("X-MBX-APIKEY"));
    assertFalse(recorded.getPath().contains("signature="));
  }

  @Test
  void shouldCallSignedAccountEndpoint() throws Exception {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                {
                  "canTrade": true,
                  "accountType": "SPOT",
                  "balances": [
                    {"asset":"BTC","free":"0.10000000","locked":"0.00000000"}
                  ]
                }
                """));

    BinanceAccountInfo accountInfo = client.getAccountInfo();

    assertTrue(accountInfo.canTrade());
    assertEquals("SPOT", accountInfo.accountType());
    assertEquals(1, accountInfo.balances().size());
    assertEquals("BTC", accountInfo.balances().get(0).asset());

    RecordedRequest recorded = server.takeRequest();
    assertEquals("GET", recorded.getMethod());
    assertEquals("test-api-key", recorded.getHeader("X-MBX-APIKEY"));
    assertEquals("/api/v3/account?" + signer.sign(Map.of()).signedQuery(), recorded.getPath());
  }
}
