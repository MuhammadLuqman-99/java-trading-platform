package com.tradingplatform.tradingapi.ledger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLedgerRepository implements LedgerRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcLedgerRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void createTransaction(UUID id, String correlationId, String type) {
    String sql =
        """
        INSERT INTO ledger_transactions (id, correlation_id, type, created_at)
        VALUES (?, ?, ?, NOW())
        """;
    jdbcTemplate.update(sql, id, correlationId, type);
  }

  @Override
  public void appendEntry(LedgerEntry entry) {
    String sql =
        """
        INSERT INTO ledger_entries (
            id,
            tx_id,
            account_id,
            asset,
            direction,
            amount,
            ref_type,
            ref_id,
            created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
        """;
    jdbcTemplate.update(
        sql,
        entry.id(),
        entry.txId(),
        entry.accountId(),
        entry.asset(),
        entry.direction().name(),
        entry.amount(),
        entry.refType(),
        entry.refId());
  }

  @Override
  public List<LedgerEntry> findEntriesByReference(String refType, String refId) {
    String sql =
        """
        SELECT id, tx_id, account_id, asset, direction, amount, ref_type, ref_id
        FROM ledger_entries
        WHERE ref_type = ? AND ref_id = ?
        ORDER BY created_at ASC
        """;
    return jdbcTemplate.query(sql, this::mapEntry, refType, refId);
  }

  private LedgerEntry mapEntry(ResultSet rs, int rowNum) throws SQLException {
    return new LedgerEntry(
        rs.getObject("id", UUID.class),
        rs.getObject("tx_id", UUID.class),
        rs.getObject("account_id", UUID.class),
        rs.getString("asset"),
        EntryDirection.valueOf(rs.getString("direction")),
        rs.getBigDecimal("amount"),
        rs.getString("ref_type"),
        rs.getString("ref_id"));
  }
}
