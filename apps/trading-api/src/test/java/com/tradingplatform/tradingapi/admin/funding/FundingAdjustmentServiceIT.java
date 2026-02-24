package com.tradingplatform.tradingapi.admin.funding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.domain.wallet.InsufficientBalanceException;
import com.tradingplatform.domain.wallet.WalletDomainException;
import com.tradingplatform.tradingapi.orders.JdbcOutboxAppendRepository;
import com.tradingplatform.tradingapi.wallet.JdbcWalletRepository;
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
class FundingAdjustmentServiceIT {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading");

  private JdbcTemplate jdbcTemplate;
  private FundingAdjustmentService service;
  private ObjectMapper objectMapper;
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
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    service =
        new FundingAdjustmentService(
            new JdbcWalletRepository(jdbcTemplate),
            new JdbcOutboxAppendRepository(jdbcTemplate, objectMapper));
    accountId = createAccount();
  }

  @Test
  void shouldCreditExistingBalanceAndAppendOutboxEvent() throws Exception {
    insertBalance(accountId, "USDT", new BigDecimal("1000.00"), BigDecimal.ZERO);

    FundingAdjustmentResult result =
        service.adjust(
            accountId,
            "usdt",
            new BigDecimal("250.00"),
            FundingDirection.CREDIT,
            "manual_sim_deposit",
            null);

    assertEquals(0, new BigDecimal("1250.00").compareTo(result.available()));
    assertEquals(1, countOutboxRows());
    assertBalance(accountId, "USDT", new BigDecimal("1250.00"), BigDecimal.ZERO);
    assertOutboxBalanceEvent(accountId, "USDT", new BigDecimal("1250.00"), BigDecimal.ZERO);
  }

  @Test
  void shouldCreateBalanceRowOnCreditWhenMissing() throws Exception {
    FundingAdjustmentResult result =
        service.adjust(
            accountId,
            "USDT",
            new BigDecimal("50.00"),
            FundingDirection.CREDIT,
            "seed_balance",
            null);

    assertEquals(0, new BigDecimal("50.00").compareTo(result.available()));
    assertBalance(accountId, "USDT", new BigDecimal("50.00"), BigDecimal.ZERO);
    assertEquals(1, countOutboxRows());
  }

  @Test
  void shouldDebitBalanceAndAppendOutboxEvent() throws Exception {
    insertBalance(accountId, "USDT", new BigDecimal("400.00"), new BigDecimal("10.00"));

    FundingAdjustmentResult result =
        service.adjust(
            accountId,
            "USDT",
            new BigDecimal("150.00"),
            FundingDirection.DEBIT,
            "manual_correction",
            null);

    assertEquals(0, new BigDecimal("250.00").compareTo(result.available()));
    assertEquals(0, new BigDecimal("10.00").compareTo(result.reserved()));
    assertBalance(accountId, "USDT", new BigDecimal("250.00"), new BigDecimal("10.00"));
    assertEquals(1, countOutboxRows());
  }

  @Test
  void shouldRejectDebitWhenInsufficientAndNotAppendOutboxEvent() {
    insertBalance(accountId, "USDT", new BigDecimal("20.00"), BigDecimal.ZERO);

    assertThrows(
        InsufficientBalanceException.class,
        () ->
            service.adjust(
                accountId,
                "USDT",
                new BigDecimal("100.00"),
                FundingDirection.DEBIT,
                "manual_correction",
                null));

    assertEquals(0, countOutboxRows());
    assertBalance(accountId, "USDT", new BigDecimal("20.00"), BigDecimal.ZERO);
  }

  @Test
  void shouldRejectMissingAccountAndNotAppendOutboxEvent() {
    assertThrows(
        WalletDomainException.class,
        () ->
            service.adjust(
                UUID.randomUUID(),
                "USDT",
                new BigDecimal("5.00"),
                FundingDirection.CREDIT,
                "manual_sim_deposit",
                null));

    assertEquals(0, countOutboxRows());
  }

  private void assertOutboxBalanceEvent(
      UUID balanceAccountId, String asset, BigDecimal available, BigDecimal reserved)
      throws Exception {
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT event_type, topic, event_key, event_payload::text AS payload FROM outbox_events");
    assertEquals("BalanceUpdated", row.get("event_type"));
    assertEquals("balances.updated.v1", row.get("topic"));
    assertEquals(balanceAccountId.toString(), row.get("event_key"));

    JsonNode payload = objectMapper.readTree(String.valueOf(row.get("payload")));
    assertEquals(balanceAccountId.toString(), payload.get("accountId").asText());
    assertEquals(asset, payload.get("asset").asText());
    assertEquals(0, available.compareTo(new BigDecimal(payload.get("available").asText())));
    assertEquals(0, reserved.compareTo(new BigDecimal(payload.get("reserved").asText())));
    assertTrue(payload.hasNonNull("reason"));
    assertTrue(payload.hasNonNull("asOf"));
  }

  private void assertBalance(
      UUID balanceAccountId, String asset, BigDecimal expectedAvailable, BigDecimal expectedReserved) {
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT available, reserved FROM wallet_balances WHERE account_id = ? AND asset = ?",
            balanceAccountId,
            asset);
    assertEquals(0, expectedAvailable.compareTo((BigDecimal) row.get("available")));
    assertEquals(0, expectedReserved.compareTo((BigDecimal) row.get("reserved")));
  }

  private int countOutboxRows() {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class);
    return count == null ? 0 : count;
  }

  private UUID createAccount() {
    UUID userId = UUID.randomUUID();
    UUID createdAccountId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', NOW(), NOW())",
        userId,
        "funding-it@example.com");
    jdbcTemplate.update(
        """
        INSERT INTO accounts (id, user_id, status, kyc_status, created_at, updated_at)
        VALUES (?, ?, 'ACTIVE', 'VERIFIED', NOW(), NOW())
        """,
        createdAccountId,
        userId);
    return createdAccountId;
  }

  private void insertBalance(UUID balanceAccountId, String asset, BigDecimal available, BigDecimal reserved) {
    jdbcTemplate.update(
        """
        INSERT INTO wallet_balances (account_id, asset, available, reserved, updated_at)
        VALUES (?, ?, ?, ?, NOW())
        """,
        balanceAccountId,
        asset,
        available,
        reserved);
  }
}
