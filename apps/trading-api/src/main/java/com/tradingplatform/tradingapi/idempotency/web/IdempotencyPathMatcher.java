package com.tradingplatform.tradingapi.idempotency.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
public class IdempotencyPathMatcher {
  private static final List<String> MUTATING_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");
  private final AntPathMatcher antPathMatcher = new AntPathMatcher();

  public boolean shouldEnforce(HttpServletRequest request, IdempotencyProperties properties) {
    if (request == null || properties == null) {
      return false;
    }
    if (!isMutatingMethod(request.getMethod())) {
      return false;
    }

    String path = request.getRequestURI();
    if (path == null || path.isBlank()) {
      return false;
    }

    for (String pattern : properties.getOptInPaths()) {
      if (pattern != null && !pattern.isBlank() && antPathMatcher.match(pattern, path)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isMutatingMethod(String method) {
    if (method == null) {
      return false;
    }
    return MUTATING_METHODS.contains(method.toUpperCase());
  }
}
