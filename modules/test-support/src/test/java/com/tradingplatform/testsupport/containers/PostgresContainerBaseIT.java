package com.tradingplatform.testsupport.containers;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class PostgresContainerBaseIT {
  @Container @ServiceConnection
  protected static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:18.2")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading_pass");
}
