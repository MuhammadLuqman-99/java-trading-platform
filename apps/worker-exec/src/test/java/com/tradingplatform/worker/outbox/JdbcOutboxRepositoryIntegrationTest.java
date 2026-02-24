package com.tradingplatform.worker.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcOutboxRepositoryIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading");

  private JdbcTemplate jdbcTemplate;
  private JdbcOutboxRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName(POSTGRES.getDriverClassName());
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());

    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load();
    flyway.clean();
    flyway.migrate();

    jdbcTemplate = new JdbcTemplate(dataSource);
    repository = new JdbcOutboxRepository(jdbcTemplate);
  }

  @Test
  void shouldClaimRowsOnlyOnceWithSkipLocked() {
    UUID id = insertOutboxRow("NEW", 0, Instant.now().minusSeconds(10), null);

    List<OutboxEventRecord> firstBatch = repository.findPendingBatch(10);
    List<OutboxEventRecord> secondBatch = repository.findPendingBatch(10);

    assertEquals(1, firstBatch.size());
    assertEquals(id, firstBatch.get(0).id());
    assertEquals(0, secondBatch.size());
    assertEquals(
        "PROCESSING",
        jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_events WHERE id = ?", String.class, id));
  }

  @Test
  void shouldMoveToDeadAfterMaxAttempts() {
    UUID id = insertOutboxRow("PROCESSING", 24, Instant.now().minusSeconds(1), Instant.now());

    repository.markFailed(id, "kafka unavailable");

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT status, attempt_count, processing_started_at FROM outbox_events WHERE id = ?", id);
    assertEquals("DEAD", row.get("status"));
    assertEquals(25, row.get("attempt_count"));
    assertNull(row.get("processing_started_at"));
  }

  @Test
  void shouldReclaimStaleProcessingRows() {
    UUID id =
        insertOutboxRow(
            "PROCESSING",
            2,
            Instant.now().plus(Duration.ofMinutes(5)),
            Instant.now().minus(Duration.ofMinutes(3)));

    List<OutboxEventRecord> claimed = repository.findPendingBatch(5);

    assertEquals(1, claimed.size());
    assertEquals(id, claimed.get(0).id());
    assertEquals("PROCESSING", claimed.get(0).status());
  }

  @Test
  void shouldApplyExponentialBackoffForRetryableFailures() {
    UUID id = insertOutboxRow("PROCESSING", 1, Instant.now(), Instant.now());

    repository.markFailed(id, "temporary");

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT status, attempt_count, next_attempt_at FROM outbox_events WHERE id = ?", id);
    assertEquals("FAILED", row.get("status"));
    assertEquals(2, row.get("attempt_count"));
    Instant nextAttemptAt = ((Timestamp) row.get("next_attempt_at")).toInstant();
    assertTrue(nextAttemptAt.isAfter(Instant.now()));
  }

  private UUID insertOutboxRow(
      String status, int attemptCount, Instant nextAttemptAt, Instant processingStartedAt) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO outbox_events (
            id,
            aggregate_type,
            aggregate_id,
            event_type,
            event_payload,
            topic,
            event_key,
            status,
            attempt_count,
            created_at,
            published_at,
            last_error,
            next_attempt_at,
            processing_started_at
        ) VALUES (?, 'ORDER', 'ord-1', 'OrderSubmitted', '{}'::jsonb, 'orders.submitted.v1',
                  'ord-1', ?, ?, NOW(), NULL, NULL, ?, ?)
        """,
        id,
        status,
        attemptCount,
        Timestamp.from(nextAttemptAt),
        processingStartedAt == null ? null : Timestamp.from(processingStartedAt));
    return id;
  }
}
