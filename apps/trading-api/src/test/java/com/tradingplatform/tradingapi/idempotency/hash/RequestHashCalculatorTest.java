package com.tradingplatform.tradingapi.idempotency.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestHashCalculatorTest {
  private final RequestHashCalculator calculator = new RequestHashCalculator();

  @Test
  void shouldGenerateSameHashForEquivalentQueryParams() {
    MockHttpServletRequest left = request("POST", "/v1/orders");
    left.addParameter("symbol", "BTCUSDT");
    left.addParameter("side", "BUY");
    left.addParameter("tags", "spot", "urgent");

    MockHttpServletRequest right = request("POST", "/v1/orders");
    right.addParameter("tags", "urgent", "spot");
    right.addParameter("side", "BUY");
    right.addParameter("symbol", "BTCUSDT");

    String leftHash = calculator.compute(left, body("{\"qty\":1}"));
    String rightHash = calculator.compute(right, body("{\"qty\":1}"));

    assertEquals(leftHash, rightHash);
  }

  @Test
  void shouldGenerateDifferentHashWhenBodyChanges() {
    MockHttpServletRequest request = request("POST", "/v1/orders");
    request.addParameter("symbol", "BTCUSDT");

    String firstHash = calculator.compute(request, body("{\"qty\":1}"));
    String secondHash = calculator.compute(request, body("{\"qty\":2}"));

    assertNotEquals(firstHash, secondHash);
  }

  @Test
  void shouldGenerateDifferentHashWhenMethodChanges() {
    MockHttpServletRequest postRequest = request("POST", "/v1/orders/1");
    MockHttpServletRequest putRequest = request("PUT", "/v1/orders/1");

    String postHash = calculator.compute(postRequest, body("{\"status\":\"OPEN\"}"));
    String putHash = calculator.compute(putRequest, body("{\"status\":\"OPEN\"}"));

    assertNotEquals(postHash, putHash);
  }

  private static MockHttpServletRequest request(String method, String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setCharacterEncoding(StandardCharsets.UTF_8.name());
    return request;
  }

  private static byte[] body(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
