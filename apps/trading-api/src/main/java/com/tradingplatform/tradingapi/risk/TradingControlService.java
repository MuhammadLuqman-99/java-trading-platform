package com.tradingplatform.tradingapi.risk;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradingControlService {
  private static final int SINGLETON_ID = 1;

  private final JdbcTemplate jdbcTemplate;

  public TradingControlService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public TradingControlState get() {
    List<TradingControlState> rows =
        jdbcTemplate.query(
            """
            SELECT trading_frozen, freeze_reason, updated_by, updated_at
            FROM trading_controls
            WHERE id = ?
            """,
            this::mapState,
            SINGLETON_ID);
    if (!rows.isEmpty()) {
      return rows.get(0);
    }

    return new TradingControlState(false, null, "system", Instant.now());
  }

  @Transactional
  public TradingControlState freeze(String reason, String actor) {
    String safeReason = normalizeReason(reason);
    String safeActor = normalizeActor(actor);
    jdbcTemplate.update(
        """
        UPDATE trading_controls
        SET trading_frozen = TRUE,
            freeze_reason = ?,
            updated_by = ?,
            updated_at = NOW()
        WHERE id = ?
        """,
        safeReason,
        safeActor,
        SINGLETON_ID);
    return get();
  }

  @Transactional
  public TradingControlState unfreeze(String actor) {
    String safeActor = normalizeActor(actor);
    jdbcTemplate.update(
        """
        UPDATE trading_controls
        SET trading_frozen = FALSE,
            freeze_reason = NULL,
            updated_by = ?,
            updated_at = NOW()
        WHERE id = ?
        """,
        safeActor,
        SINGLETON_ID);
    return get();
  }

  private TradingControlState mapState(ResultSet rs, int rowNum) throws SQLException {
    return new TradingControlState(
        rs.getBoolean("trading_frozen"),
        rs.getString("freeze_reason"),
        rs.getString("updated_by"),
        rs.getTimestamp("updated_at").toInstant());
  }

  private static String normalizeReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return "manual_freeze";
    }
    return reason;
  }

  private static String normalizeActor(String actor) {
    if (actor == null || actor.isBlank()) {
      return "admin";
    }
    return actor;
  }
}
