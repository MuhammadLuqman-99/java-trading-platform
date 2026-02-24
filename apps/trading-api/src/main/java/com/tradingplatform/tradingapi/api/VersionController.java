package com.tradingplatform.tradingapi.api;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class VersionController {
  private static final String DEFAULT_APP_NAME = "trading-api";
  private static final String DEFAULT_VERSION = "unknown";

  private final ObjectProvider<BuildProperties> buildPropertiesProvider;
  private final String applicationName;

  public VersionController(
      ObjectProvider<BuildProperties> buildPropertiesProvider,
      @Value("${spring.application.name:" + DEFAULT_APP_NAME + "}") String applicationName) {
    this.buildPropertiesProvider = buildPropertiesProvider;
    this.applicationName = applicationName;
  }

  @GetMapping("/version")
  public VersionResponse version() {
    BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
    if (buildProperties == null) {
      return new VersionResponse(applicationName, DEFAULT_VERSION, null);
    }

    String version = valueOrDefault(buildProperties.getVersion(), DEFAULT_VERSION);
    return new VersionResponse(applicationName, version, buildProperties.getTime());
  }

  private static String valueOrDefault(String value, String defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return value;
  }
}
