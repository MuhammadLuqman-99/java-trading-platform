package com.tradingplatform.tradingapi.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {
  private RateLimitFilter filter;
  private RateLimitProperties properties;
  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOps;
  private FilterChain filterChain;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    properties = new RateLimitProperties();
    properties.setEnabled(true);
    properties.setMaxRequests(5);
    properties.setWindowSeconds(60);
    properties.setOptInPaths(List.of("/v1/orders/**"));

    redisTemplate = mock(StringRedisTemplate.class);
    valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);

    filter = new RateLimitFilter(properties, redisTemplate);
    filterChain = mock(FilterChain.class);
  }

  @Test
  void shouldPassThroughWhenUnderLimit() throws Exception {
    when(valueOps.increment(anyString())).thenReturn(3L);

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/orders");
    request.setRemoteAddr("127.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(any(), any());
    assertEquals("5", response.getHeader("X-RateLimit-Limit"));
    assertEquals("2", response.getHeader("X-RateLimit-Remaining"));
  }

  @Test
  void shouldReturn429WhenOverLimit() throws Exception {
    when(valueOps.increment(anyString())).thenReturn(6L);

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/orders");
    request.setRemoteAddr("127.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    verify(filterChain, never()).doFilter(any(), any());
    assertEquals(429, response.getStatus());
    assertEquals("60", response.getHeader("Retry-After"));
  }

  @Test
  void shouldSkipNonMatchingPaths() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/version");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(any(), any());
    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  void shouldMatchNestedPaths() throws Exception {
    when(valueOps.increment(anyString())).thenReturn(1L);

    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/v1/orders/some-id/cancel");
    request.setRemoteAddr("127.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(any(), any());
    assertEquals("5", response.getHeader("X-RateLimit-Limit"));
  }
}
