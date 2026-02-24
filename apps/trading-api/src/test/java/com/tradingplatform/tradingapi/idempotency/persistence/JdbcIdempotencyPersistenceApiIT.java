package com.tradingplatform.tradingapi.idempotency.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tradingplatform.testsupport.containers.PostgresContainerBaseIT;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.flyway.enabled=true",
      "idempotency.enabled=true",
      "spring.datasource.driver-class-name=org.postgresql.Driver"
    })
class JdbcIdempotencyPersistenceApiIT extends PostgresContainerBaseIT {
  @Autowired private IdempotencyPersistenceApi persistenceApi;

  @Test
  void shouldCreateAndLoadInProgressRecord() {
    Instant expiresAt = Instant.parse("2026-02-25T00:00:00Z");

    IdempotencyRecord created =
        persistenceApi.createInProgress("orders", "idem-it-1", "hash-it-1", expiresAt);
    Optional<IdempotencyRecord> found = persistenceApi.findByScopeAndKey("orders", "idem-it-1");

    assertTrue(found.isPresent());
    assertEquals(created.id(), found.get().id());
    assertEquals(IdempotencyStatus.IN_PROGRESS, found.get().status());
    assertEquals("hash-it-1", found.get().requestHash());
    assertEquals(expiresAt, found.get().expiresAt());
  }

  @Test
  void shouldTransitionToCompletedAndFailedStates() {
    IdempotencyRecord created =
        persistenceApi.createInProgress(
            "orders", "idem-it-2", "hash-it-2", Instant.parse("2026-02-26T00:00:00Z"));

    persistenceApi.markCompleted(created.id(), 201, "{\"result\":\"ok\"}");

    IdempotencyRecord completed =
        persistenceApi.findByScopeAndKey("orders", "idem-it-2").orElseThrow();
    assertEquals(IdempotencyStatus.COMPLETED, completed.status());
    assertEquals(201, completed.responseCode());
    assertNotNull(completed.responseBody());

    persistenceApi.markFailed(created.id(), "KAFKA_DOWNSTREAM_TIMEOUT");

    IdempotencyRecord failed =
        persistenceApi.findByScopeAndKey("orders", "idem-it-2").orElseThrow();
    assertEquals(IdempotencyStatus.FAILED, failed.status());
    assertEquals("KAFKA_DOWNSTREAM_TIMEOUT", failed.errorCode());
  }
}
