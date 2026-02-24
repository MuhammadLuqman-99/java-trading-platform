package com.tradingplatform.tradingapi.idempotency.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcIdempotencyPersistenceApiIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading");

  private JdbcTemplate jdbcTemplate;
  private JdbcIdempotencyPersistenceApi persistenceApi;

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
    persistenceApi = new JdbcIdempotencyPersistenceApi(jdbcTemplate);
  }

  @Test
  void shouldPersistCompletedStateWithRequiredResponseCode() {
    IdempotencyRecord created =
        persistenceApi.createInProgress(
            "POST:/v1/orders", "key-1", "hash-1", Instant.now().plusSeconds(3600));

    persistenceApi.markCompleted(created.id(), 201, null);

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT status, response_code, error_code FROM idempotency_keys WHERE id = ?",
            created.id());
    assertEquals("COMPLETED", row.get("status"));
    assertEquals(201, row.get("response_code"));
    assertNull(row.get("error_code"));
  }

  @Test
  void shouldNormalizeBlankErrorCodeOnFailure() {
    IdempotencyRecord created =
        persistenceApi.createInProgress(
            "POST:/v1/orders", "key-2", "hash-2", Instant.now().plusSeconds(3600));

    persistenceApi.markFailed(created.id(), "  ");

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT status, error_code FROM idempotency_keys WHERE id = ?", created.id());
    assertEquals("FAILED", row.get("status"));
    assertEquals("UNSPECIFIED_ERROR", row.get("error_code"));
  }

  @Test
  void shouldRejectInvalidCompletedShapeWithoutResponseCode() {
    IdempotencyRecord created =
        persistenceApi.createInProgress(
            "POST:/v1/orders", "key-3", "hash-3", Instant.now().plusSeconds(3600));

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                """
                UPDATE idempotency_keys
                SET status = 'COMPLETED',
                    response_code = NULL,
                    error_code = NULL
                WHERE id = ?
                """,
                created.id()));
  }

  @Test
  void shouldRejectDuplicateScopeAndKeyEvenIfExpired() {
    Instant createdAt = Instant.now().minusSeconds(7200);
    Instant expiresAt = Instant.now().minusSeconds(3600);

    jdbcTemplate.update(
        """
        INSERT INTO idempotency_keys (
            id,
            idempotency_key,
            scope,
            request_hash,
            status,
            response_code,
            response_body,
            error_code,
            created_at,
            updated_at,
            expires_at
        ) VALUES (?, ?, ?, ?, 'COMPLETED', 200, NULL, NULL, ?, ?, ?)
        """,
        UUID.randomUUID(),
        "key-4",
        "POST:/v1/orders",
        "hash-initial",
        Timestamp.from(createdAt),
        Timestamp.from(createdAt),
        Timestamp.from(expiresAt));

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            persistenceApi.createInProgress(
                "POST:/v1/orders", "key-4", "hash-new", Instant.now().plusSeconds(3600)));
  }
}
