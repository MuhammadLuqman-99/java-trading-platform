package com.tradingplatform.testsupport.containers;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class KafkaContainerBaseIT {
  @Container @ServiceConnection
  protected static final KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.0"));

  protected static String bootstrapServers() {
    return kafka.getBootstrapServers();
  }
}
