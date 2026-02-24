package com.tradingplatform.tradingapi.ledger;

import com.tradingplatform.domain.wallet.InsufficientBalanceException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminFundingService {
  private final JdbcTemplate jdbcTemplate;
  private final LedgerRepository ledgerRepository;

  public AdminFundingService(JdbcTemplate jdbcTemplate, LedgerRepository ledgerRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.ledgerRepository = ledgerRepository;
  }

  @Transactional
  public UUID postAdjustment(
      UUID accountId,
      String asset,
      BigDecimal amount,
      String reason,
      FundingDirection direction,
      String actor) {
    BalanceRow balance = findOrCreateBalance(accountId, asset, amount, direction);
    if (direction == FundingDirection.DEBIT && balance.available().compareTo(amount) < 0) {
      throw new InsufficientBalanceException(accountId, asset, amount, balance.available());
    }

    BigDecimal nextAvailable =
        direction == FundingDirection.CREDIT
            ? balance.available().add(amount)
            : balance.available().subtract(amount);
    jdbcTemplate.update(
        """
        UPDATE wallet_balances
        SET available = ?,
            updated_at = NOW()
        WHERE account_id = ? AND asset = ?
        """,
        nextAvailable,
        accountId,
        asset);

    UUID txId = UUID.randomUUID();
    String txType = direction == FundingDirection.CREDIT ? "ADMIN_CREDIT" : "ADMIN_DEBIT";
    ledgerRepository.createTransaction(txId, txId.toString(), txType);

    EntryDirection primaryDirection =
        direction == FundingDirection.CREDIT ? EntryDirection.CREDIT : EntryDirection.DEBIT;
    EntryDirection contraDirection =
        direction == FundingDirection.CREDIT ? EntryDirection.DEBIT : EntryDirection.CREDIT;

    ledgerRepository.appendEntry(
        new LedgerEntry(
            UUID.randomUUID(),
            txId,
            accountId,
            asset,
            primaryDirection,
            amount,
            "ADMIN_ADJUSTMENT",
            txId.toString()));

    ledgerRepository.appendEntry(
        new LedgerEntry(
            UUID.randomUUID(),
            txId,
            accountId,
            asset,
            contraDirection,
            amount,
            "PLATFORM_OFFSET",
            txId.toString()));

    return txId;
  }

  private BalanceRow findOrCreateBalance(
      UUID accountId, String asset, BigDecimal requestedAmount, FundingDirection direction) {
    List<BalanceRow> rows =
        jdbcTemplate.query(
            """
            SELECT account_id, asset, available, reserved
            FROM wallet_balances
            WHERE account_id = ? AND asset = ?
            FOR UPDATE
            """,
            this::mapBalance,
            accountId,
            asset);
    if (!rows.isEmpty()) {
      return rows.get(0);
    }

    if (direction == FundingDirection.DEBIT) {
      throw new InsufficientBalanceException(accountId, asset, requestedAmount, BigDecimal.ZERO);
    }

    jdbcTemplate.update(
        """
        INSERT INTO wallet_balances (account_id, asset, available, reserved, updated_at)
        VALUES (?, ?, 0, 0, NOW())
        """,
        accountId,
        asset);
    return new BalanceRow(accountId, asset, BigDecimal.ZERO, BigDecimal.ZERO);
  }

  private BalanceRow mapBalance(ResultSet rs, int rowNum) throws SQLException {
    return new BalanceRow(
        rs.getObject("account_id", UUID.class),
        rs.getString("asset"),
        rs.getBigDecimal("available"),
        rs.getBigDecimal("reserved"));
  }

  private record BalanceRow(UUID accountId, String asset, BigDecimal available, BigDecimal reserved) {}
}
