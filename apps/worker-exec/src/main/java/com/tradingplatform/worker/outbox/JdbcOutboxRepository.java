package com.tradingplatform.worker.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcOutboxRepository implements OutboxRepository {
  private static final int MAX_ATTEMPTS = 25;
  private final JdbcTemplate jdbcTemplate;

  public JdbcOutboxRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public List<OutboxEventRecord> findPendingBatch(int limit) {
    int safeLimit = Math.max(1, limit);
    String reclaimStaleSql =
        """
                UPDATE outbox_events
                SET status = 'FAILED',
                    next_attempt_at = NOW(),
                    processing_started_at = NULL,
                    last_error = COALESCE(last_error, 'Reclaimed stale processing lease')
                WHERE status = 'PROCESSING'
                  AND processing_started_at < NOW() - INTERVAL '2 minutes'
                """;
    jdbcTemplate.update(reclaimStaleSql);

    String sql =
        """
                WITH claimable AS (
                    SELECT id
                    FROM outbox_events
                    WHERE status IN ('NEW', 'FAILED')
                      AND next_attempt_at <= NOW()
                    ORDER BY created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE outbox_events outbox
                SET status = 'PROCESSING',
                    processing_started_at = NOW()
                FROM claimable
                WHERE outbox.id = claimable.id
                RETURNING outbox.id,
                          outbox.aggregate_type,
                          outbox.aggregate_id,
                          outbox.event_type,
                          outbox.event_payload,
                          outbox.topic,
                          outbox.event_key,
                          outbox.status,
                          outbox.attempt_count,
                          outbox.created_at
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
                    last_error = NULL,
                    processing_started_at = NULL
                WHERE id = ?
                """;
    jdbcTemplate.update(sql, publishedAt, id);
  }

  @Override
  public void markFailed(UUID id, String errorMessage) {
    String sql =
        """
                UPDATE outbox_events
                SET status = CASE
                                WHEN attempt_count + 1 >= ? THEN 'DEAD'
                                ELSE 'FAILED'
                             END,
                    attempt_count = attempt_count + 1,
                    last_error = ?,
                    processing_started_at = NULL,
                    next_attempt_at = CASE
                                        WHEN attempt_count + 1 >= ? THEN next_attempt_at
                                        ELSE NOW() + (INTERVAL '5 seconds' * POWER(2, LEAST(attempt_count + 1, 6)))
                                      END
                WHERE id = ?
                """;
    jdbcTemplate.update(sql, MAX_ATTEMPTS, errorMessage, MAX_ATTEMPTS, id);
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
