package com.tradingplatform.tradingapi.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.domain.orders.Order;
import com.tradingplatform.domain.orders.OrderDomainException;
import com.tradingplatform.domain.orders.OrderSide;
import com.tradingplatform.domain.orders.OrderStatus;
import com.tradingplatform.domain.orders.OrderType;
import com.tradingplatform.tradingapi.risk.AccountLimitService;
import com.tradingplatform.tradingapi.risk.TradingControlService;
import com.tradingplatform.tradingapi.wallet.JdbcWalletRepository;
import com.tradingplatform.tradingapi.wallet.WalletReservationService;
import java.math.BigDecimal;
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
class OrderApplicationServiceIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading");

  private JdbcTemplate jdbcTemplate;
  private OrderApplicationService service;
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
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    WalletReservationService walletReservationService =
        new WalletReservationService(new JdbcWalletRepository(jdbcTemplate));
    service =
        new OrderApplicationService(
            new JdbcOrderRepository(jdbcTemplate),
            new JdbcOrderEventRepository(jdbcTemplate),
            new JdbcOutboxAppendRepository(jdbcTemplate, objectMapper),
            walletReservationService,
            new TradingControlService(jdbcTemplate),
            new AccountLimitService(jdbcTemplate),
            objectMapper);
    accountId = createAccount();
  }

  @Test
  void shouldCreateOrderAndAppendOrderEventAndOutboxEvent() {
    UUID orderId = UUID.randomUUID();

    Order order =
        service.createOrder(
            new CreateOrderCommand(
                orderId,
                accountId,
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("0.5"),
                new BigDecimal("45000"),
                "client-1",
                "corr-1",
                null));

    assertEquals(OrderStatus.NEW, order.status());
    assertEquals(1, queryCount("SELECT COUNT(*) FROM orders"));
    assertEquals(1, queryCount("SELECT COUNT(*) FROM order_events"));
    assertEquals(2, queryCount("SELECT COUNT(*) FROM outbox_events"));

    assertEquals(
        1,
        queryCount(
            "SELECT COUNT(*) FROM outbox_events WHERE event_key = '"
                + orderId
                + "' AND event_type = 'OrderSubmitted' AND topic = 'orders.submitted.v1'"));
    assertEquals(
        1,
        queryCount(
            "SELECT COUNT(*) FROM outbox_events WHERE event_key = '"
                + orderId
                + "' AND event_type = 'OrderSubmitted' AND topic = 'orders.submitted.v2'"));
  }

  @Test
  void shouldTransitionOrderAndAppendEvents() {
    UUID orderId = UUID.randomUUID();
    service.createOrder(
        new CreateOrderCommand(
            orderId,
            accountId,
            "ETHUSDT",
            OrderSide.SELL,
            OrderType.MARKET,
            new BigDecimal("2"),
            null,
            "client-2",
            "corr-2",
            null));

    Order updated =
        service.transitionOrder(
            new TransitionOrderCommand(
                orderId,
                OrderStatus.ACK,
                BigDecimal.ZERO,
                "BINANCE",
                "exch-1",
                "exch-client-1",
                "exchange_ack",
                "corr-3",
                null));

    assertEquals(OrderStatus.ACK, updated.status());
    assertEquals(2, queryCount("SELECT COUNT(*) FROM order_events"));
    assertEquals(4, queryCount("SELECT COUNT(*) FROM outbox_events"));
    assertEquals(
        "ACK", jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId));
    assertEquals(
        "BINANCE",
        jdbcTemplate.queryForObject(
            "SELECT exchange_name FROM orders WHERE id = ?", String.class, orderId));
  }

  @Test
  void shouldNotAppendEventsWhenTransitionIsInvalid() {
    UUID orderId = UUID.randomUUID();
    service.createOrder(
        new CreateOrderCommand(
            orderId,
            accountId,
            "SOLUSDT",
            OrderSide.BUY,
            OrderType.LIMIT,
            new BigDecimal("10"),
            new BigDecimal("120"),
            "client-3",
            "corr-4",
            null));

    assertThrows(
        OrderDomainException.class,
        () ->
            service.transitionOrder(
                new TransitionOrderCommand(
                    orderId,
                    OrderStatus.FILLED,
                    new BigDecimal("10"),
                    "BINANCE",
                    "exch-2",
                    "exch-client-2",
                    "invalid_direct_fill",
                    "corr-5",
                    null)));

    assertEquals(1, queryCount("SELECT COUNT(*) FROM order_events"));
    assertEquals(2, queryCount("SELECT COUNT(*) FROM outbox_events"));
    assertEquals(
        "NEW", jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId));
  }

  private UUID createAccount() {
    UUID userId = UUID.randomUUID();
    UUID createdAccountId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', NOW(), NOW())",
        userId,
        "it-user@example.com");
    jdbcTemplate.update(
        """
        INSERT INTO accounts (id, user_id, status, kyc_status, created_at, updated_at)
        VALUES (?, ?, 'ACTIVE', 'VERIFIED', NOW(), NOW())
        """,
        createdAccountId,
        userId);
    return createdAccountId;
  }

  private int queryCount(String sql) {
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
    return count == null ? 0 : count;
  }
}
