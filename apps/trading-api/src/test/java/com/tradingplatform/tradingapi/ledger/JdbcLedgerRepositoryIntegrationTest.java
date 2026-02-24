package com.tradingplatform.tradingapi.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
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
class JdbcLedgerRepositoryIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading");

  private JdbcTemplate jdbcTemplate;
  private JdbcLedgerRepository repository;
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
    repository = new JdbcLedgerRepository(jdbcTemplate);
    accountId = createAccount();
  }

  @Test
  void shouldCreateTransactionAndAppendEntries() {
    UUID txId = UUID.randomUUID();
    repository.createTransaction(txId, "corr-1", "ORDER_RESERVED");
    repository.appendEntry(
        new LedgerEntry(
            UUID.randomUUID(),
            txId,
            accountId,
            "USDT",
            EntryDirection.DEBIT,
            new BigDecimal("100"),
            "ORDER",
            "ord-1"));
    repository.appendEntry(
        new LedgerEntry(
            UUID.randomUUID(),
            txId,
            accountId,
            "USDT",
            EntryDirection.CREDIT,
            new BigDecimal("100"),
            "ORDER",
            "ord-1"));

    List<LedgerEntry> entries = repository.findEntriesByReference("ORDER", "ord-1");
    assertEquals(2, entries.size());
    assertEquals(EntryDirection.DEBIT, entries.get(0).direction());
    assertEquals(EntryDirection.CREDIT, entries.get(1).direction());
  }

  private UUID createAccount() {
    UUID userId = UUID.randomUUID();
    UUID createdAccountId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', NOW(), NOW())",
        userId,
        "ledger-it@example.com");
    jdbcTemplate.update(
        """
        INSERT INTO accounts (id, user_id, status, kyc_status, created_at, updated_at)
        VALUES (?, ?, 'ACTIVE', 'VERIFIED', NOW(), NOW())
        """,
        createdAccountId,
        userId);
    return createdAccountId;
  }
}
