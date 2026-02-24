package com.tradingplatform.tradingapi.api;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
public class AdminSampleController {
  @GetMapping("/ping")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, String> ping() {
    return Map.of("status", "ok", "scope", "admin");
  }
}
