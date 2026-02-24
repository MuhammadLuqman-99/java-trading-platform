package com.tradingplatform.tradingapi.wallet;

import com.tradingplatform.domain.wallet.ReservationStatus;
import com.tradingplatform.domain.wallet.WalletBalance;
import com.tradingplatform.domain.wallet.WalletReservation;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWalletRepository implements WalletRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcWalletRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public boolean accountExists(UUID accountId) {
    String sql = "SELECT EXISTS(SELECT 1 FROM accounts WHERE id = ?)";
    Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, accountId);
    return Boolean.TRUE.equals(exists);
  }

  @Override
  public Optional<WalletBalance> findBalanceForUpdate(UUID accountId, String asset) {
    String sql =
        """
        SELECT account_id, asset, available, reserved, updated_at
        FROM wallet_balances
        WHERE account_id = ? AND asset = ?
        FOR UPDATE
        """;
    List<WalletBalance> rows = jdbcTemplate.query(sql, this::mapBalance, accountId, asset);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
  }

  @Override
  public void insertBalance(
      UUID accountId, String asset, BigDecimal available, BigDecimal reserved) {
    String sql =
        """
        INSERT INTO wallet_balances (account_id, asset, available, reserved, updated_at)
        VALUES (?, ?, ?, ?, NOW())
        """;
    jdbcTemplate.update(sql, accountId, asset, available, reserved);
  }

  @Override
  public void updateBalance(
      UUID accountId, String asset, BigDecimal available, BigDecimal reserved) {
    String sql =
        """
        UPDATE wallet_balances
        SET available = ?, reserved = ?, updated_at = NOW()
        WHERE account_id = ? AND asset = ?
        """;
    jdbcTemplate.update(sql, available, reserved, accountId, asset);
  }

  @Override
  public void insertReservation(WalletReservation reservation) {
    String sql =
        """
        INSERT INTO wallet_reservations (id, account_id, asset, amount, order_id, status, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
    jdbcTemplate.update(
        sql,
        reservation.id(),
        reservation.accountId(),
        reservation.asset(),
        reservation.amount(),
        reservation.orderId(),
        reservation.status().name(),
        Timestamp.from(reservation.createdAt()));
  }

  @Override
  public Optional<WalletReservation> findActiveReservationByOrderId(UUID orderId) {
    String sql =
        """
        SELECT id, account_id, asset, amount, order_id, status, created_at, released_at
        FROM wallet_reservations
        WHERE order_id = ? AND status = 'ACTIVE'
        """;
    List<WalletReservation> rows = jdbcTemplate.query(sql, this::mapReservation, orderId);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
  }

  @Override
  public void updateReservationStatus(UUID reservationId, ReservationStatus status) {
    String sql =
        """
        UPDATE wallet_reservations
        SET status = ?,
            released_at = CASE WHEN ? IN ('RELEASED','CANCELLED','CONSUMED') THEN NOW() ELSE released_at END
        WHERE id = ?
        """;
    jdbcTemplate.update(sql, status.name(), status.name(), reservationId);
  }

  private WalletBalance mapBalance(ResultSet rs, int rowNum) throws SQLException {
    return new WalletBalance(
        rs.getObject("account_id", UUID.class),
        rs.getString("asset"),
        rs.getBigDecimal("available"),
        rs.getBigDecimal("reserved"),
        rs.getTimestamp("updated_at").toInstant());
  }

  private WalletReservation mapReservation(ResultSet rs, int rowNum) throws SQLException {
    Timestamp releasedAt = rs.getTimestamp("released_at");
    return new WalletReservation(
        rs.getObject("id", UUID.class),
        rs.getObject("account_id", UUID.class),
        rs.getString("asset"),
        rs.getBigDecimal("amount"),
        rs.getObject("order_id", UUID.class),
        ReservationStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toInstant(),
        releasedAt != null ? releasedAt.toInstant() : null);
  }
}
