package com.tradingplatform.worker.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOutboxRepository implements OutboxRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcOutboxRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<OutboxEventRecord> findPendingBatch(int limit) {
    int safeLimit = Math.max(1, limit);
    String sql =
        """
                SELECT id,
                       aggregate_type,
                       aggregate_id,
                       event_type,
                       event_payload,
                       topic,
                       event_key,
                       status,
                       attempt_count,
                       created_at
                FROM outbox_events
                WHERE status = 'NEW'
                ORDER BY created_at ASC
                LIMIT ?
                """;
    return jdbcTemplate.query(sql, this::mapRecord, safeLimit);
  }

  @Override
  public void markPublished(UUID id, Instant publishedAt) {
    String sql =
        """
                UPDATE outbox_events
                SET status = 'PUBLISHED',
                    published_at = ?,
                    last_error = NULL
                WHERE id = ?
                """;
    jdbcTemplate.update(sql, publishedAt, id);
  }

  @Override
  public void markFailed(UUID id, String errorMessage) {
    String sql =
        """
                UPDATE outbox_events
                SET status = 'FAILED',
                    attempt_count = attempt_count + 1,
                    last_error = ?
                WHERE id = ?
                """;
    jdbcTemplate.update(sql, errorMessage, id);
  }

  private OutboxEventRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
    return new OutboxEventRecord(
        rs.getObject("id", UUID.class),
        rs.getString("aggregate_type"),
        rs.getString("aggregate_id"),
        rs.getString("event_type"),
        rs.getString("event_payload"),
        rs.getString("topic"),
        rs.getString("event_key"),
        rs.getString("status"),
        rs.getInt("attempt_count"),
        rs.getTimestamp("created_at").toInstant());
  }
}
