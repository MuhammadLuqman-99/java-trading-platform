package com.tradingplatform.tradingapi.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(
    prefix = "rate-limit",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class RateLimitFilter extends OncePerRequestFilter {
  private final RateLimitProperties properties;
  private final StringRedisTemplate redisTemplate;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  public RateLimitFilter(RateLimitProperties properties, StringRedisTemplate redisTemplate) {
    this.properties = properties;
    this.redisTemplate = redisTemplate;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!matchesOptInPath(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    String identity = extractIdentity(request);
    long windowTimestamp = System.currentTimeMillis() / (properties.getWindowSeconds() * 1000L);
    String key =
        properties.getKeyPrefix() + ":" + identity + ":" + windowTimestamp;

    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redisTemplate.expire(key, Duration.ofSeconds(properties.getWindowSeconds()));
    }

    long remaining = Math.max(0, properties.getMaxRequests() - (count != null ? count : 0));
    response.setHeader("X-RateLimit-Limit", String.valueOf(properties.getMaxRequests()));
    response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

    if (count != null && count > properties.getMaxRequests()) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.setHeader("Retry-After", String.valueOf(properties.getWindowSeconds()));
      response
          .getWriter()
          .write(
              "{\"code\":\"RATE_LIMIT_EXCEEDED\","
                  + "\"message\":\"Too many requests. Please try again later.\"}");
      return;
    }

    filterChain.doFilter(request, response);
  }

  private boolean matchesOptInPath(HttpServletRequest request) {
    String path = request.getRequestURI();
    for (String pattern : properties.getOptInPaths()) {
      if (pathMatcher.match(pattern, path)) {
        return true;
      }
    }
    return false;
  }

  private String extractIdentity(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      String sub = jwt.getSubject();
      if (sub != null && !sub.isBlank()) {
        return sub;
      }
    }
    return request.getRemoteAddr();
  }
}
