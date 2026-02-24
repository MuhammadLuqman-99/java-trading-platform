package com.tradingplatform.tradingapi.audit;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditLogRepository implements AuditLogRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcAuditLogRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void append(AuditLogEntry entry) {
    String sql =
        """
        INSERT INTO audit_log (
            id,
            actor_user_id,
            action,
            entity_type,
            entity_id,
            before_json,
            after_json,
            result,
            error_code,
            error_message,
            metadata_json,
            created_at
        ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), ?, ?, ?, CAST(? AS JSONB), NOW())
        """;
    jdbcTemplate.update(
        sql,
        UUID.randomUUID(),
        entry.actorUserId(),
        entry.action(),
        entry.entityType(),
        entry.entityId(),
        entry.beforeJson(),
        entry.afterJson(),
        entry.result().name(),
        entry.errorCode(),
        entry.errorMessage(),
        entry.metadataJson());
  }
}
