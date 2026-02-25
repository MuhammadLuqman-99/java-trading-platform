package com.tradingplatform.tradingapi.connector;

import java.util.Optional;

public interface ConnectorHealthQueryService {
  Optional<ConnectorHealthSnapshot> findByConnectorName(String connectorName);
}
