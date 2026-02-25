package com.tradingplatform.tradingapi.executions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
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
class ExecutionQueryServiceIT {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading");

  private JdbcTemplate jdbcTemplate;
  private ExecutionQueryService queryService;

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
    queryService = new ExecutionQueryService(new JdbcExecutionReadRepository(jdbcTemplate));
  }

  @Test
  void shouldQueryExecutionsWithFiltersAndPagination() {
    UUID accountId = createAccount("exec-query-it@example.com");
    UUID order1 = insertOrder(accountId, "BTCUSDT", "BUY", "binance-ord-11");
    UUID order2 = insertOrder(accountId, "ETHUSDT", "SELL", "binance-ord-12");

    insertExecution(
        order1,
        accountId,
        "BTCUSDT",
        "BUY",
        "trade-1001",
        "binance-ord-11",
        new BigDecimal("0.10"),
        new BigDecimal("42000"),
        "USDT",
        new BigDecimal("1.2"),
        Instant.parse("2026-02-25T10:00:00Z"));
    insertExecution(
        order2,
        accountId,
        "ETHUSDT",
        "SELL",
        "trade-1002",
        "binance-ord-12",
        new BigDecimal("1.50"),
        new BigDecimal("2300"),
        "USDT",
        new BigDecimal("0.8"),
        Instant.parse("2026-02-25T11:00:00Z"));

    ExecutionQueryService.ExecutionPage page =
        queryService.listExecutions(
            accountId,
            null,
            "ETHUSDT",
            Instant.parse("2026-02-25T10:30:00Z"),
            Instant.parse("2026-02-25T11:30:00Z"),
            0,
            20);

    assertEquals(1, page.executions().size());
    assertEquals(order2, page.executions().getFirst().orderId());
    assertEquals("ETHUSDT", page.executions().getFirst().symbol());
    assertEquals(1L, page.totalElements());
    assertEquals(1, page.totalPages());
  }

  private UUID createAccount(String email) {
    UUID userId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', NOW(), NOW())",
        userId,
        email);
    jdbcTemplate.update(
        """
        INSERT INTO accounts (id, user_id, status, kyc_status, created_at, updated_at)
        VALUES (?, ?, 'ACTIVE', 'VERIFIED', NOW(), NOW())
        """,
        accountId,
        userId);
    return accountId;
  }

  private UUID insertOrder(UUID accountId, String symbol, String side, String exchangeOrderId) {
    UUID orderId = UUID.randomUUID();
    String type = "BUY".equals(side) ? "LIMIT" : "MARKET";
    BigDecimal price = "LIMIT".equals(type) ? new BigDecimal("42000") : null;
    jdbcTemplate.update(
        """
        INSERT INTO orders (
          id, account_id, instrument, side, type, qty, price, status, filled_qty,
          client_order_id, exchange_name, exchange_order_id, exchange_client_order_id, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACK', 0, ?, 'BINANCE', ?, ?, NOW(), NOW())
        """,
        orderId,
        accountId,
        symbol,
        side,
        type,
        new BigDecimal("2.0"),
        price,
        orderId.toString(),
        exchangeOrderId,
        "cli-" + orderId);
    return orderId;
  }

  private void insertExecution(
      UUID orderId,
      UUID accountId,
      String symbol,
      String side,
      String tradeId,
      String exchangeOrderId,
      BigDecimal qty,
      BigDecimal price,
      String feeAsset,
      BigDecimal feeAmount,
      Instant executedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO executions (
          id, order_id, account_id, instrument, trade_id, exchange_name, exchange_order_id,
          side, qty, price, fee_asset, fee_amount, executed_at, created_at
        ) VALUES (?, ?, ?, ?, ?, 'BINANCE', ?, ?, ?, ?, ?, ?, ?, NOW())
        """,
        UUID.randomUUID(),
        orderId,
        accountId,
        symbol,
        tradeId,
        exchangeOrderId,
        side,
        qty,
        price,
        feeAsset,
        feeAmount,
        executedAt);
  }
}
