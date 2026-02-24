package com.tradingplatform.tradingapi.idempotency.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IdempotencyPathMatcherTest {
  private final IdempotencyPathMatcher matcher = new IdempotencyPathMatcher();

  @Test
  void shouldEnforceForMutatingRequestOnOptInPath() {
    IdempotencyProperties properties = properties(List.of("/v1/orders/**"));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/orders/123");

    assertTrue(matcher.shouldEnforce(request, properties));
  }

  @Test
  void shouldNotEnforceForReadOnlyRequest() {
    IdempotencyProperties properties = properties(List.of("/v1/orders/**"));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/orders/123");

    assertFalse(matcher.shouldEnforce(request, properties));
  }

  @Test
  void shouldNotEnforceWhenPathIsOutsideOptInPatterns() {
    IdempotencyProperties properties = properties(List.of("/v1/orders/**"));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/admin/ping");

    assertFalse(matcher.shouldEnforce(request, properties));
  }

  private static IdempotencyProperties properties(List<String> optInPaths) {
    IdempotencyProperties properties = new IdempotencyProperties();
    properties.setOptInPaths(optInPaths);
    return properties;
  }
}
