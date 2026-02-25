package com.tradingplatform.worker.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.integration.binance.BinanceTradeSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
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
class BinanceFillProcessorIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading");

  private JdbcTemplate jdbcTemplate;
  private BinanceFillProcessor fillProcessor;

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
    fillProcessor =
        new BinanceFillProcessor(
            jdbcTemplate, new ObjectMapper().registerModule(new JavaTimeModule()), new SimpleMeterRegistry());
  }

  @Test
  void shouldPersistExecutionUpdateOrderBalancesAndOutbox() {
    UUID accountId = createAccount("fills-it@example.com");
    UUID orderId = UUID.randomUUID();

    insertInstrument("BTCUSDT", "BTC", "USDT");
    insertOrder(
        orderId,
        accountId,
        "BTCUSDT",
        "BUY",
        new BigDecimal("0.01000000"),
        BigDecimal.ZERO,
        "ACK",
        "binance-ord-1",
        "cli-1");
    upsertBalance(accountId, "BTC", BigDecimal.ZERO, BigDecimal.ZERO);
    upsertBalance(accountId, "USDT", new BigDecimal("1000.00"), new BigDecimal("510.00"));
    insertActiveReservation(accountId, orderId, "USDT", new BigDecimal("510.00"));

    FillProcessingOutcome outcome =
        fillProcessor.processTrade(
            new BinanceTradeSnapshot(
                "BTCUSDT",
                "trade-9001",
                "binance-ord-1",
                "BUY",
                new BigDecimal("0.01000000"),
                new BigDecimal("50000.00"),
                "USDT",
                new BigDecimal("1.00"),
                Instant.parse("2026-02-25T12:00:00Z")));

    assertEquals(FillProcessingOutcome.INSERTED, outcome);
    assertEquals("FILLED", queryString("SELECT status FROM orders WHERE id = ?", orderId));
    assertDecimalEquals(
        new BigDecimal("0.01000000"),
        queryDecimal("SELECT filled_qty FROM orders WHERE id = ?", orderId));
    assertDecimalEquals(
        new BigDecimal("0.01000000"),
        queryDecimal(
            "SELECT available FROM wallet_balances WHERE account_id = ? AND asset = 'BTC'", accountId));
    assertDecimalEquals(
        new BigDecimal("1009.00"),
        queryDecimal(
            "SELECT available FROM wallet_balances WHERE account_id = ? AND asset = 'USDT'", accountId));
    assertDecimalEquals(
        BigDecimal.ZERO,
        queryDecimal(
            "SELECT reserved FROM wallet_balances WHERE account_id = ? AND asset = 'USDT'", accountId));
    assertEquals(
        "RELEASED",
        queryString("SELECT status FROM wallet_reservations WHERE order_id = ?", orderId));

    assertEquals(1, queryCount("SELECT COUNT(*) FROM executions"));
    assertEquals(1, queryCount("SELECT COUNT(*) FROM ledger_transactions"));
    assertEquals(1, queryCount("SELECT COUNT(*) FROM order_events WHERE order_id = ?", orderId));
    UUID ledgerTxId =
        queryUuid(
            "SELECT id FROM ledger_transactions WHERE correlation_id = (SELECT CAST(id AS TEXT) FROM executions WHERE trade_id = ?)",
            "trade-9001");
    assertEquals("ORDER_FILL", queryString("SELECT type FROM ledger_transactions WHERE id = ?", ledgerTxId));
    assertEquals(6, queryCount("SELECT COUNT(*) FROM ledger_entries WHERE tx_id = ?", ledgerTxId));
    assertEquals(
        2,
        queryCount(
            "SELECT COUNT(*) FROM ledger_entries WHERE tx_id = ? AND asset = 'USDT' AND direction = 'DEBIT'",
            ledgerTxId));
    assertEquals(
        2,
        queryCount(
            "SELECT COUNT(*) FROM ledger_entries WHERE tx_id = ? AND asset = 'USDT' AND direction = 'CREDIT'",
            ledgerTxId));
    Map<String, BigDecimal> ledgerNetByAsset = queryLedgerNetByAsset(ledgerTxId);
    assertDecimalEquals(BigDecimal.ZERO, ledgerNetByAsset.get("BTC"));
    assertDecimalEquals(BigDecimal.ZERO, ledgerNetByAsset.get("USDT"));

    assertEquals(
        1,
        queryCount(
            "SELECT COUNT(*) FROM outbox_events WHERE topic = 'executions.recorded.v1' AND event_key = ?",
            orderId.toString()));
    assertEquals(
        1,
        queryCount(
            "SELECT COUNT(*) FROM outbox_events WHERE topic = 'orders.updated.v1' AND event_key = ?",
            orderId.toString()));
    assertEquals(
        1,
        queryCount(
            "SELECT COUNT(*) FROM outbox_events WHERE topic = 'orders.updated.v2' AND event_key = ?",
            orderId.toString()));
    assertEquals(
        3,
        queryCount(
            "SELECT COUNT(*) FROM outbox_events WHERE topic = 'balances.updated.v1' AND event_key = ?",
            accountId.toString()));
  }

  @Test
  void shouldTreatDuplicateTradeAsIdempotent() {
    UUID accountId = createAccount("fills-dup-it@example.com");
    UUID orderId = UUID.randomUUID();

    insertInstrument("ETHUSDT", "ETH", "USDT");
    insertOrder(
        orderId,
        accountId,
        "ETHUSDT",
        "SELL",
        new BigDecimal("0.50000000"),
        BigDecimal.ZERO,
        "ACK",
        "binance-ord-2",
        "cli-2");
    upsertBalance(accountId, "ETH", BigDecimal.ZERO, new BigDecimal("0.50000000"));
    upsertBalance(accountId, "USDT", new BigDecimal("200.00"), BigDecimal.ZERO);
    insertActiveReservation(accountId, orderId, "ETH", new BigDecimal("0.50000000"));

    BinanceTradeSnapshot trade =
        new BinanceTradeSnapshot(
            "ETHUSDT",
            "trade-dup-1",
            "binance-ord-2",
            "SELL",
            new BigDecimal("0.50000000"),
            new BigDecimal("2200.00"),
            "USDT",
            new BigDecimal("2.20"),
            Instant.parse("2026-02-25T13:00:00Z"));

    FillProcessingOutcome first = fillProcessor.processTrade(trade);
    FillProcessingOutcome second = fillProcessor.processTrade(trade);

    assertEquals(FillProcessingOutcome.INSERTED, first);
    assertEquals(FillProcessingOutcome.DUPLICATE, second);
    assertEquals(1, queryCount("SELECT COUNT(*) FROM executions"));
    assertEquals(1, queryCount("SELECT COUNT(*) FROM ledger_transactions"));
    assertEquals(6, queryCount("SELECT COUNT(*) FROM ledger_entries"));
    assertEquals(
        1,
        queryCount(
            "SELECT COUNT(*) FROM outbox_events WHERE topic = 'executions.recorded.v1' AND event_key = ?",
            orderId.toString()));
  }

  @Test
  void shouldReturnUnmappedWhenOrderDoesNotExist() {
    insertInstrument("BTCUSDT", "BTC", "USDT");
    FillProcessingOutcome outcome =
        fillProcessor.processTrade(
            new BinanceTradeSnapshot(
                "BTCUSDT",
                "trade-missing-order",
                "unknown-exchange-order",
                "BUY",
                new BigDecimal("0.10000000"),
                new BigDecimal("40000.00"),
                "USDT",
                new BigDecimal("0.80"),
                Instant.parse("2026-02-25T14:00:00Z")));

    assertEquals(FillProcessingOutcome.UNMAPPED, outcome);
    assertEquals(0, queryCount("SELECT COUNT(*) FROM executions"));
    assertEquals(0, queryCount("SELECT COUNT(*) FROM outbox_events"));
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

  private void insertInstrument(String symbol, String baseAsset, String quoteAsset) {
    jdbcTemplate.update(
        """
        INSERT INTO instruments (
          id, symbol, status, reference_price, base_asset, quote_asset, created_at, updated_at
        ) VALUES (?, ?, 'ACTIVE', ?, ?, ?, NOW(), NOW())
        """,
        UUID.randomUUID(),
        symbol,
        new BigDecimal("1000"),
        baseAsset,
        quoteAsset);
  }

  private void insertOrder(
      UUID orderId,
      UUID accountId,
      String instrument,
      String side,
      BigDecimal qty,
      BigDecimal filledQty,
      String status,
      String exchangeOrderId,
      String exchangeClientOrderId) {
    String type = "BUY".equals(side) ? "LIMIT" : "MARKET";
    BigDecimal price = "LIMIT".equals(type) ? new BigDecimal("50000") : null;
    jdbcTemplate.update(
        """
        INSERT INTO orders (
          id, account_id, instrument, side, type, qty, price, status, filled_qty,
          client_order_id, exchange_name, exchange_order_id, exchange_client_order_id, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'BINANCE', ?, ?, NOW(), NOW())
        """,
        orderId,
        accountId,
        instrument,
        side,
        type,
        qty,
        price,
        status,
        filledQty,
        orderId.toString(),
        exchangeOrderId,
        exchangeClientOrderId);
  }

  private void upsertBalance(UUID accountId, String asset, BigDecimal available, BigDecimal reserved) {
    jdbcTemplate.update(
        """
        INSERT INTO wallet_balances (account_id, asset, available, reserved, updated_at)
        VALUES (?, ?, ?, ?, NOW())
        """,
        accountId,
        asset,
        available,
        reserved);
  }

  private void insertActiveReservation(UUID accountId, UUID orderId, String asset, BigDecimal amount) {
    jdbcTemplate.update(
        """
        INSERT INTO wallet_reservations (id, account_id, asset, amount, order_id, status, created_at)
        VALUES (?, ?, ?, ?, ?, 'ACTIVE', NOW())
        """,
        UUID.randomUUID(),
        accountId,
        asset,
        amount,
        orderId);
  }

  private int queryCount(String sql, Object... args) {
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
    return count == null ? 0 : count;
  }

  private String queryString(String sql, Object... args) {
    return jdbcTemplate.queryForObject(sql, String.class, args);
  }

  private BigDecimal queryDecimal(String sql, Object... args) {
    return jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
  }

  private UUID queryUuid(String sql, Object... args) {
    return jdbcTemplate.queryForObject(sql, UUID.class, args);
  }

  private Map<String, BigDecimal> queryLedgerNetByAsset(UUID transactionId) {
    String sql =
        """
        SELECT asset,
               SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END) AS net_amount
        FROM ledger_entries
        WHERE tx_id = ?
        GROUP BY asset
        """;
    Map<String, BigDecimal> result = new LinkedHashMap<>();
    jdbcTemplate.query(
        sql,
        rs -> {
          result.put(rs.getString("asset"), rs.getBigDecimal("net_amount"));
        },
        transactionId);
    return result;
  }

  private static void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
    assertEquals(0, expected.compareTo(actual));
  }
}
