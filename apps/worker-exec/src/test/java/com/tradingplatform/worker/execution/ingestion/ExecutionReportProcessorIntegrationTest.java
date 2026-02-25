package com.tradingplatform.worker.execution.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.domain.orders.OrderStatus;
import com.tradingplatform.integration.binance.DatabaseBackedExchangeOrderStatusMapper;
import com.tradingplatform.integration.binance.ExchangeOrderStatusMapping;
import com.tradingplatform.integration.binance.ExchangeOrderStatusMappingRepository;
import com.tradingplatform.integration.binance.BinanceExecutionReport;
import com.tradingplatform.integration.binance.BinanceVenue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
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
class ExecutionReportProcessorIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading");

  private JdbcTemplate jdbcTemplate;
  private ExecutionReportProcessor processor;

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
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ExchangeOrderStatusMappingRepository mappingRepository =
        () ->
            List.of(
                new ExchangeOrderStatusMapping(
                    BinanceVenue.BINANCE_SPOT, "NEW", OrderStatus.NEW, false, true),
                new ExchangeOrderStatusMapping(
                    BinanceVenue.BINANCE_SPOT,
                    "PARTIALLY_FILLED",
                    OrderStatus.PARTIALLY_FILLED,
                    false,
                    true),
                new ExchangeOrderStatusMapping(
                    BinanceVenue.BINANCE_SPOT, "FILLED", OrderStatus.FILLED, true, false));
    DatabaseBackedExchangeOrderStatusMapper mapper =
        new DatabaseBackedExchangeOrderStatusMapper(mappingRepository, new SimpleMeterRegistry());
    ExecutionRepository executionRepository = new JdbcExecutionRepository(jdbcTemplate);
    processor = new ExecutionReportProcessor(jdbcTemplate, objectMapper, mapper, executionRepository);
  }

  @Test
  void shouldUpsertExecutionsAndUpdateOrderExactlyOncePerTrade() {
    UUID accountId = createAccount();
    UUID orderId = createAckOrder(accountId, "9001001", "ord-1001");

    ExecutionIngestionResult first =
        processor.process(
            executionReport(
                "7001001",
                "9001001",
                "ord-1001",
                "PARTIALLY_FILLED",
                new BigDecimal("0.40"),
                new BigDecimal("0.40"),
                Instant.parse("2026-02-26T00:00:00Z")));
    assertEquals(ExecutionIngestionResult.PROCESSED, first);
    assertEquals("PARTIALLY_FILLED", findOrderStatus(orderId));
    assertEquals(new BigDecimal("0.40"), findOrderFilledQty(orderId));
    assertEquals(1, queryCount("SELECT COUNT(*) FROM executions WHERE order_id = ?", orderId));
    assertEquals(
        3,
        queryCount(
            "SELECT COUNT(*) FROM outbox_events WHERE event_key = ? AND event_type IN ('OrderUpdated', 'ExecutionRecorded')",
            orderId.toString()));

    ExecutionIngestionResult duplicate =
        processor.process(
            executionReport(
                "7001001",
                "9001001",
                "ord-1001",
                "PARTIALLY_FILLED",
                new BigDecimal("0.40"),
                new BigDecimal("0.40"),
                Instant.parse("2026-02-26T00:00:00Z")));
    assertEquals(ExecutionIngestionResult.DUPLICATE, duplicate);
    assertEquals(1, queryCount("SELECT COUNT(*) FROM executions WHERE order_id = ?", orderId));
    assertEquals(
        3,
        queryCount(
            "SELECT COUNT(*) FROM outbox_events WHERE event_key = ? AND event_type IN ('OrderUpdated', 'ExecutionRecorded')",
            orderId.toString()));

    ExecutionIngestionResult second =
        processor.process(
            executionReport(
                "7001002",
                "9001001",
                "ord-1001",
                "FILLED",
                new BigDecimal("0.60"),
                new BigDecimal("1.00"),
                Instant.parse("2026-02-26T00:00:01Z")));
    assertEquals(ExecutionIngestionResult.PROCESSED, second);
    assertEquals("FILLED", findOrderStatus(orderId));
    assertEquals(new BigDecimal("1.00"), findOrderFilledQty(orderId));
    assertEquals(2, queryCount("SELECT COUNT(*) FROM executions WHERE order_id = ?", orderId));
    assertEquals(
        6,
        queryCount(
            "SELECT COUNT(*) FROM outbox_events WHERE event_key = ? AND event_type IN ('OrderUpdated', 'ExecutionRecorded')",
            orderId.toString()));
  }

  private UUID createAccount() {
    UUID userId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', NOW(), NOW())",
        userId,
        "execution-it-user@example.com");
    jdbcTemplate.update(
        """
        INSERT INTO accounts (id, user_id, status, kyc_status, created_at, updated_at)
        VALUES (?, ?, 'ACTIVE', 'VERIFIED', NOW(), NOW())
        """,
        accountId,
        userId);
    return accountId;
  }

  private UUID createAckOrder(UUID accountId, String exchangeOrderId, String exchangeClientOrderId) {
    UUID orderId = UUID.randomUUID();
    Instant now = Instant.parse("2026-02-26T00:00:00Z");
    jdbcTemplate.update(
        """
        INSERT INTO orders (
            id,
            account_id,
            instrument,
            side,
            type,
            qty,
            price,
            status,
            filled_qty,
            client_order_id,
            exchange_name,
            exchange_order_id,
            exchange_client_order_id,
            created_at,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        orderId,
        accountId,
        "BTCUSDT",
        "BUY",
        "LIMIT",
        new BigDecimal("1.00"),
        new BigDecimal("43000.10"),
        "ACK",
        new BigDecimal("0.00"),
        exchangeClientOrderId,
        "BINANCE",
        exchangeOrderId,
        exchangeClientOrderId,
        now,
        now);
    return orderId;
  }

  private String findOrderStatus(UUID orderId) {
    return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
  }

  private BigDecimal findOrderFilledQty(UUID orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT filled_qty FROM orders WHERE id = ?", BigDecimal.class, orderId);
  }

  private int queryCount(String sql, Object... args) {
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
    return count == null ? 0 : count;
  }

  private static BinanceExecutionReport executionReport(
      String exchangeTradeId,
      String exchangeOrderId,
      String exchangeClientOrderId,
      String externalStatus,
      BigDecimal lastQty,
      BigDecimal cumulativeQty,
      Instant tradeTime) {
    return new BinanceExecutionReport(
        externalStatus,
        exchangeOrderId,
        exchangeClientOrderId,
        "BTCUSDT",
        "BUY",
        exchangeTradeId,
        lastQty,
        cumulativeQty,
        new BigDecimal("43000.10"),
        "USDT",
        new BigDecimal("0.0005"),
        tradeTime,
        "{\"e\":\"executionReport\",\"x\":\"TRADE\"}");
  }
}
