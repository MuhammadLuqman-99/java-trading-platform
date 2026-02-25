package com.tradingplatform.integration.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class BinanceSpotOrderClientLiveIT {
  @Test
  void shouldSubmitMarketOrderWhenLiveSmokeEnabled() {
    boolean runLiveSmoke = Boolean.parseBoolean(System.getenv("BINANCE_RUN_LIVE_SMOKE"));
    String apiKey = System.getenv("BINANCE_API_KEY");
    String apiSecret = System.getenv("BINANCE_API_SECRET");

    assumeTrue(runLiveSmoke, "Set BINANCE_RUN_LIVE_SMOKE=true to run live testnet smoke");
    assumeTrue(apiKey != null && !apiKey.isBlank(), "Set BINANCE_API_KEY for live smoke");
    assumeTrue(apiSecret != null && !apiSecret.isBlank(), "Set BINANCE_API_SECRET for live smoke");

    String baseUrl = System.getenv().getOrDefault("BINANCE_TESTNET_BASE_URL", "https://testnet.binance.vision");
    String clientOrderId = "live-smoke-" + System.currentTimeMillis();

    BinanceApiConfig config =
        new BinanceApiConfig(
            URI.create(baseUrl),
            apiKey,
            apiSecret,
            5000L,
            Duration.ofSeconds(10),
            Clock.systemUTC());
    HttpBinanceOrderClient client =
        new HttpBinanceOrderClient(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
            new ObjectMapper(),
            config,
            new BinanceRequestSigner(apiSecret, 5000L, Clock.systemUTC()));

    BinanceOrderSubmitResponse response =
        client.submitOrder(
            new BinanceOrderSubmitRequest(
                "BTCUSDT", "BUY", "MARKET", new BigDecimal("0.001"), null, clientOrderId));

    assertNotNull(response.exchangeOrderId());
    assertEquals(clientOrderId, response.clientOrderId());
  }
}
