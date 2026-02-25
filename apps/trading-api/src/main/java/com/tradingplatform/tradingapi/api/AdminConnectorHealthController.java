package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.connector.ConnectorHealthProperties;
import com.tradingplatform.tradingapi.connector.ConnectorHealthQueryService;
import com.tradingplatform.tradingapi.connector.ConnectorHealthSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/admin/connector")
@PreAuthorize("hasRole('ADMIN')")
public class AdminConnectorHealthController {
  private static final String CONNECTOR_NAME = "binance-spot";

  private final ConnectorHealthQueryService connectorHealthQueryService;
  private final ConnectorHealthProperties connectorHealthProperties;
  private final Clock clock;

  @Autowired
  public AdminConnectorHealthController(
      ConnectorHealthQueryService connectorHealthQueryService,
      ConnectorHealthProperties connectorHealthProperties) {
    this(connectorHealthQueryService, connectorHealthProperties, Clock.systemUTC());
  }

  AdminConnectorHealthController(
      ConnectorHealthQueryService connectorHealthQueryService,
      ConnectorHealthProperties connectorHealthProperties,
      Clock clock) {
    this.connectorHealthQueryService = connectorHealthQueryService;
    this.connectorHealthProperties = connectorHealthProperties;
    this.clock = clock;
  }

  @GetMapping("/health")
  public ConnectorHealthResponse health() {
    ConnectorHealthSnapshot snapshot =
        connectorHealthQueryService
            .findByConnectorName(CONNECTOR_NAME)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Connector health not found: " + CONNECTOR_NAME));
    return ConnectorHealthResponse.from(snapshot, isStale(snapshot.lastSuccessAt()));
  }

  private boolean isStale(Instant lastSuccessAt) {
    if (lastSuccessAt == null) {
      return true;
    }
    long thresholdMinutes = Math.max(1L, connectorHealthProperties.getStaleThresholdMinutes());
    Duration age = Duration.between(lastSuccessAt, clock.instant());
    return age.toMinutes() >= thresholdMinutes;
  }
}
