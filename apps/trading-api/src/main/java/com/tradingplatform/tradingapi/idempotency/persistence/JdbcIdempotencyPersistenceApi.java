package com.tradingplatform.tradingapi.idempotency.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
    prefix = "idempotency",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JdbcIdempotencyPersistenceApi implements IdempotencyPersistenceApi {
  private final JdbcTemplate jdbcTemplate;

  public JdbcIdempotencyPersistenceApi(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<IdempotencyRecord> findByScopeAndKey(String scope, String idempotencyKey) {
    String sql =
        """
        SELECT id,
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
        FROM idempotency_keys
        WHERE scope = ? AND idempotency_key = ?
        """;
    List<IdempotencyRecord> rows = jdbcTemplate.query(sql, this::mapRecord, scope, idempotencyKey);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rows.get(0));
  }

  @Override
  public IdempotencyRecord createInProgress(
      String scope, String idempotencyKey, String requestHash, Instant expiresAt) {
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();

    String sql =
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
        ) VALUES (?, ?, ?, ?, 'IN_PROGRESS', NULL, NULL, NULL, ?, ?, ?)
        """;
    jdbcTemplate.update(sql, id, idempotencyKey, scope, requestHash, now, now, expiresAt);

    return new IdempotencyRecord(
        id,
        idempotencyKey,
        scope,
        requestHash,
        IdempotencyStatus.IN_PROGRESS,
        null,
        null,
        null,
        now,
        now,
        expiresAt);
  }

  @Override
  public void markCompleted(UUID id, int responseCode, String responseBody) {
    String sql =
        """
        UPDATE idempotency_keys
        SET status = 'COMPLETED',
            response_code = ?,
            response_body = CAST(? AS JSONB),
            error_code = NULL,
            updated_at = NOW()
        WHERE id = ?
        """;
    jdbcTemplate.update(sql, responseCode, responseBody, id);
  }

  @Override
  public void markFailed(UUID id, String errorCode) {
    String sql =
        """
        UPDATE idempotency_keys
        SET status = 'FAILED',
            error_code = ?,
            updated_at = NOW()
        WHERE id = ?
        """;
    jdbcTemplate.update(sql, errorCode, id);
  }

  private IdempotencyRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
    return new IdempotencyRecord(
        rs.getObject("id", UUID.class),
        rs.getString("idempotency_key"),
        rs.getString("scope"),
        rs.getString("request_hash"),
        IdempotencyStatus.valueOf(rs.getString("status")),
        rs.getObject("response_code", Integer.class),
        rs.getString("response_body"),
        rs.getString("error_code"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        rs.getTimestamp("expires_at").toInstant());
  }
}
