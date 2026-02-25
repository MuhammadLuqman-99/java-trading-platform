package com.tradingplatform.worker.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.worker.execution.ExecutionAckResult;
import com.tradingplatform.worker.execution.SubmitOrderCommand;
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
class OrderSubmissionProcessorIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading");

  private JdbcTemplate jdbcTemplate;
  private OrderSubmissionProcessor processor;

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
    processor =
        new OrderSubmissionProcessor(
            jdbcTemplate,
            objectMapper,
            command -> new ExecutionAckResult("BINANCE", "binance-9001", command.orderId()));
  }

  @Test
  void shouldAcknowledgeOrderAndKeepDuplicateDeliveryIdempotent() {
    UUID accountId = createAccount();
    UUID orderId = createOrder(accountId);
    UUID eventId = UUID.randomUUID();
    SubmitOrderCommand command =
        new SubmitOrderCommand(
            orderId.toString(),
            accountId.toString(),
            "BTCUSDT",
            "BUY",
            "LIMIT",
            new BigDecimal("0.50"),
            new BigDecimal("45000.00"),
            "client-9001",
            Instant.parse("2026-02-25T01:00:00Z"),
            "corr-9001",
            eventId);

    processor.process(command);
    processor.process(command);

    assertEquals(
        "ACK", jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId));
    assertEquals(
        "BINANCE",
        jdbcTemplate.queryForObject("SELECT exchange_name FROM orders WHERE id = ?", String.class, orderId));
    assertEquals(
        "binance-9001",
        jdbcTemplate.queryForObject(
            "SELECT exchange_order_id FROM orders WHERE id = ?", String.class, orderId));
    assertEquals(
        orderId.toString(),
        jdbcTemplate.queryForObject(
            "SELECT exchange_client_order_id FROM orders WHERE id = ?", String.class, orderId));
    assertEquals(1, queryCount("SELECT COUNT(*) FROM processed_kafka_events WHERE event_id = '" + eventId + "'"));
    assertEquals(1, queryCount("SELECT COUNT(*) FROM order_events WHERE order_id = '" + orderId + "'"));
    assertEquals(
        2,
        queryCount(
            "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'OrderUpdated' AND event_key = '"
                + orderId
                + "'"));
  }

  private UUID createAccount() {
    UUID userId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', NOW(), NOW())",
        userId,
        "worker-it-user@example.com");
    jdbcTemplate.update(
        """
        INSERT INTO accounts (id, user_id, status, kyc_status, created_at, updated_at)
        VALUES (?, ?, 'ACTIVE', 'VERIFIED', NOW(), NOW())
        """,
        accountId,
        userId);
    return accountId;
  }

  private UUID createOrder(UUID accountId) {
    UUID orderId = UUID.randomUUID();
    Instant now = Instant.parse("2026-02-25T00:59:00Z");
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
            created_at,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        orderId,
        accountId,
        "BTCUSDT",
        "BUY",
        "LIMIT",
        new BigDecimal("0.50"),
        new BigDecimal("45000.00"),
        "NEW",
        BigDecimal.ZERO,
        "client-9001",
        now,
        now);
    return orderId;
  }

  private int queryCount(String sql) {
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
    return count == null ? 0 : count;
  }
}
