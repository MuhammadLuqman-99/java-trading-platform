package com.tradingplatform.worker.connector;

import java.util.Optional;

public interface ConnectorHealthRepository {
  Optional<ConnectorHealthState> findByConnectorName(String connectorName);

  void upsert(ConnectorHealthState state);
}
