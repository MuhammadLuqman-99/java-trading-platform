package com.tradingplatform.tradingapi.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tradingplatform.domain.wallet.InsufficientBalanceException;
import com.tradingplatform.domain.wallet.WalletDomainException;
import com.tradingplatform.domain.wallet.WalletReservation;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WalletReservationServiceIT {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading");

  private JdbcTemplate jdbcTemplate;
  private WalletReservationService service;
  private UUID accountId;

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
    service = new WalletReservationService(new JdbcWalletRepository(jdbcTemplate));
    accountId = createAccountWithBalance("USDT", new BigDecimal("1000.00"));
  }

  @Test
  void shouldReserveAndDeductFromAvailable() {
    UUID orderId = UUID.randomUUID();
    WalletReservation reservation =
        service.reserve(accountId, "USDT", new BigDecimal("250.00"), orderId);

    assertEquals(orderId, reservation.orderId());
    assertEquals("ACTIVE", reservation.status().name());

    Map<String, Object> balance = getBalance(accountId, "USDT");
    assertEquals(0, new BigDecimal("750.00").compareTo((BigDecimal) balance.get("available")));
    assertEquals(0, new BigDecimal("250.00").compareTo((BigDecimal) balance.get("reserved")));
  }

  @Test
  void shouldThrowInsufficientBalanceWhenNotEnough() {
    assertThrows(
        InsufficientBalanceException.class,
        () -> service.reserve(accountId, "USDT", new BigDecimal("1500.00"), UUID.randomUUID()));

    Map<String, Object> balance = getBalance(accountId, "USDT");
    assertEquals(0, new BigDecimal("1000.00").compareTo((BigDecimal) balance.get("available")));
    assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) balance.get("reserved")));
  }

  @Test
  void shouldThrowWhenNoBalanceExists() {
    assertThrows(
        WalletDomainException.class,
        () -> service.reserve(accountId, "BTC", new BigDecimal("1.00"), UUID.randomUUID()));
  }

  @Test
  void shouldReleaseReservationAndRestoreAvailable() {
    UUID orderId = UUID.randomUUID();
    service.reserve(accountId, "USDT", new BigDecimal("300.00"), orderId);

    service.release(orderId);

    Map<String, Object> balance = getBalance(accountId, "USDT");
    assertEquals(0, new BigDecimal("1000.00").compareTo((BigDecimal) balance.get("available")));
    assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) balance.get("reserved")));

    String reservationStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM wallet_reservations WHERE order_id = ?",
            String.class,
            orderId);
    assertEquals("CANCELLED", reservationStatus);
  }

  @Test
  void shouldConsumeReservationAndDeductFromReserved() {
    UUID orderId = UUID.randomUUID();
    service.reserve(accountId, "USDT", new BigDecimal("200.00"), orderId);

    service.consume(orderId);

    Map<String, Object> balance = getBalance(accountId, "USDT");
    assertEquals(0, new BigDecimal("800.00").compareTo((BigDecimal) balance.get("available")));
    assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) balance.get("reserved")));

    String reservationStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM wallet_reservations WHERE order_id = ?",
            String.class,
            orderId);
    assertEquals("CONSUMED", reservationStatus);
  }

  @Test
  void shouldBeIdempotentOnReleaseWhenNoActiveReservation() {
    service.release(UUID.randomUUID());

    Map<String, Object> balance = getBalance(accountId, "USDT");
    assertEquals(0, new BigDecimal("1000.00").compareTo((BigDecimal) balance.get("available")));
  }

  @Test
  void shouldBeIdempotentOnDoubleRelease() {
    UUID orderId = UUID.randomUUID();
    service.reserve(accountId, "USDT", new BigDecimal("100.00"), orderId);

    service.release(orderId);
    service.release(orderId);

    Map<String, Object> balance = getBalance(accountId, "USDT");
    assertEquals(0, new BigDecimal("1000.00").compareTo((BigDecimal) balance.get("available")));
    assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) balance.get("reserved")));
  }

  private UUID createAccountWithBalance(String asset, BigDecimal available) {
    UUID userId = UUID.randomUUID();
    UUID createdAccountId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', NOW(), NOW())",
        userId,
        "wallet-it@example.com");
    jdbcTemplate.update(
        """
        INSERT INTO accounts (id, user_id, status, kyc_status, created_at, updated_at)
        VALUES (?, ?, 'ACTIVE', 'VERIFIED', NOW(), NOW())
        """,
        createdAccountId,
        userId);
    jdbcTemplate.update(
        """
        INSERT INTO wallet_balances (account_id, asset, available, reserved, updated_at)
        VALUES (?, ?, ?, 0, NOW())
        """,
        createdAccountId,
        asset,
        available);
    return createdAccountId;
  }

  private Map<String, Object> getBalance(UUID balanceAccountId, String asset) {
    return jdbcTemplate.queryForMap(
        "SELECT available, reserved FROM wallet_balances WHERE account_id = ? AND asset = ?",
        balanceAccountId,
        asset);
  }
}
