package com.tradingplatform.tradingapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TradingApiApplicationTest {
  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void healthEndpointShouldReturnUp() {
    ResponseEntity<Map> response =
        restTemplate.getForEntity(baseUrl("/actuator/health"), Map.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("UP", response.getBody().get("status"));
  }

  @Test
  void versionEndpointShouldReturnApplicationAndVersion() {
    ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl("/v1/version"), Map.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    Object application = response.getBody().get("application");
    Object version = response.getBody().get("version");
    assertEquals("trading-api", application);
    assertTrue(version instanceof String);
    assertTrue(!((String) version).isBlank());
  }

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
  }
}
