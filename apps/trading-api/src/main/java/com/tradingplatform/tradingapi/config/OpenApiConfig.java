package com.tradingplatform.tradingapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  private static final String DEFAULT_APP_NAME = "trading-api";
  private static final String DEFAULT_VERSION = "unknown";

  @Bean
  public OpenAPI tradingApiOpenApi(
      ObjectProvider<BuildProperties> buildPropertiesProvider,
      @Value("${spring.application.name:" + DEFAULT_APP_NAME + "}") String applicationName) {
    BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
    String version = buildProperties != null ? buildProperties.getVersion() : DEFAULT_VERSION;
    if (version == null || version.isBlank()) {
      version = DEFAULT_VERSION;
    }

    return new OpenAPI()
        .info(
            new Info()
                .title(applicationName)
                .version(version)
                .description("Trading API OpenAPI documentation"));
  }

  @Bean
  public GroupedOpenApi publicApiGroup() {
    return GroupedOpenApi.builder()
        .group("public")
        .pathsToMatch("/v1/**")
        .pathsToExclude("/v1/version")
        .build();
  }

  @Bean
  public GroupedOpenApi opsApiGroup() {
    return GroupedOpenApi.builder()
        .group("ops")
        .pathsToMatch("/v1/version", "/actuator/health", "/actuator/health/**")
        .build();
  }
}
