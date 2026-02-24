package com.tradingplatform.tradingapi.risk;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountLimitService {
  public static final int DEFAULT_PRICE_BAND_BPS = 10_000;

  private final JdbcTemplate jdbcTemplate;

  public AccountLimitService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public AccountLimitConfig upsert(
      UUID accountId, BigDecimal maxOrderNotional, Integer priceBandBps, String actor) {
    int safePriceBand = normalizePriceBand(priceBandBps);
    String safeActor = normalizeActor(actor);

    jdbcTemplate.update(
        """
        INSERT INTO account_limits (
            account_id,
            max_order_notional,
            price_band_bps,
            created_at,
            updated_at,
            updated_by
        ) VALUES (?, ?, ?, NOW(), NOW(), ?)
        ON CONFLICT (account_id) DO UPDATE
        SET max_order_notional = EXCLUDED.max_order_notional,
            price_band_bps = EXCLUDED.price_band_bps,
            updated_by = EXCLUDED.updated_by,
            updated_at = NOW()
        """,
        accountId,
        maxOrderNotional,
        safePriceBand,
        safeActor);

    return findByAccountId(accountId)
        .orElseThrow(() -> new IllegalStateException("Failed to read account limits after upsert"));
  }

  @Transactional(readOnly = true)
  public Optional<AccountLimitConfig> findByAccountId(UUID accountId) {
    List<AccountLimitConfig> rows =
        jdbcTemplate.query(
            """
            SELECT account_id, max_order_notional, price_band_bps, updated_by, updated_at
            FROM account_limits
            WHERE account_id = ?
            """,
            this::mapAccountLimit,
            accountId);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rows.get(0));
  }

  private AccountLimitConfig mapAccountLimit(ResultSet rs, int rowNum) throws SQLException {
    return new AccountLimitConfig(
        rs.getObject("account_id", UUID.class),
        rs.getBigDecimal("max_order_notional"),
        rs.getInt("price_band_bps"),
        rs.getString("updated_by"),
        rs.getTimestamp("updated_at").toInstant());
  }

  private static int normalizePriceBand(Integer priceBandBps) {
    if (priceBandBps == null) {
      return DEFAULT_PRICE_BAND_BPS;
    }
    return Math.max(0, priceBandBps);
  }

  private static String normalizeActor(String actor) {
    if (actor == null || actor.isBlank()) {
      return "admin";
    }
    return actor;
  }
}
