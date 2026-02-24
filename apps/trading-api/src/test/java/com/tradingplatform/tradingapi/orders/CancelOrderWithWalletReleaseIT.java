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
class CancelOrderWithWalletReleaseIT {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading");

  private JdbcTemplate jdbcTemplate;
  private OrderApplicationService orderService;
  private WalletReservationService walletService;
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
    walletService = new WalletReservationService(new JdbcWalletRepository(jdbcTemplate));
    orderService =
        new OrderApplicationService(
            new JdbcOrderRepository(jdbcTemplate),
            new JdbcOrderEventRepository(jdbcTemplate),
            new JdbcOutboxAppendRepository(jdbcTemplate, objectMapper),
            walletService,
            new TradingControlService(jdbcTemplate),
            new AccountLimitService(jdbcTemplate),
            objectMapper);
    accountId = createAccountWithBalance("USDT", new BigDecimal("5000.00"));
  }

  @Test
  void shouldReleaseWalletReservationWhenOrderCanceled() {
    UUID orderId = UUID.randomUUID();
    orderService.createOrder(
        new CreateOrderCommand(
            orderId, accountId, "BTCUSDT", OrderSide.BUY, OrderType.LIMIT,
            new BigDecimal("0.1"), new BigDecimal("45000"), "client-1", "corr-1", null));

    walletService.reserve(accountId, "USDT", new BigDecimal("4500.00"), orderId);

    Map<String, Object> balanceBefore = getBalance(accountId, "USDT");
    assertEquals(0, new BigDecimal("500.00").compareTo((BigDecimal) balanceBefore.get("available")));
    assertEquals(0, new BigDecimal("4500.00").compareTo((BigDecimal) balanceBefore.get("reserved")));

    Order canceled =
        orderService.cancelOrder(
            new CancelOrderCommand(orderId, accountId, "user_requested", "corr-2", null));

    assertEquals(OrderStatus.CANCELED, canceled.status());

    Map<String, Object> balanceAfter = getBalance(accountId, "USDT");
    assertEquals(0, new BigDecimal("5000.00").compareTo((BigDecimal) balanceAfter.get("available")));
    assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) balanceAfter.get("reserved")));

    String reservationStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM wallet_reservations WHERE order_id = ?", String.class, orderId);
    assertEquals("CANCELLED", reservationStatus);
  }

  @Test
  void shouldCancelOrderEvenWithoutWalletReservation() {
    UUID orderId = UUID.randomUUID();
    orderService.createOrder(
        new CreateOrderCommand(
            orderId, accountId, "ETHUSDT", OrderSide.SELL, OrderType.MARKET,
            new BigDecimal("2"), null, "client-2", "corr-3", null));

    Order canceled =
        orderService.cancelOrder(
            new CancelOrderCommand(orderId, accountId, "user_requested", "corr-4", null));

    assertEquals(OrderStatus.CANCELED, canceled.status());

    Map<String, Object> balance = getBalance(accountId, "USDT");
    assertEquals(0, new BigDecimal("5000.00").compareTo((BigDecimal) balance.get("available")));
  }

  @Test
  void shouldRejectCancelForTerminalOrder() {
    UUID orderId = UUID.randomUUID();
    orderService.createOrder(
        new CreateOrderCommand(
            orderId, accountId, "BTCUSDT", OrderSide.BUY, OrderType.MARKET,
            new BigDecimal("0.5"), null, "client-3", "corr-5", null));
    orderService.cancelOrder(
        new CancelOrderCommand(orderId, accountId, "first_cancel", "corr-6", null));

    assertThrows(
        OrderDomainException.class,
        () ->
            orderService.cancelOrder(
                new CancelOrderCommand(orderId, accountId, "second_cancel", "corr-7", null)));
  }

  @Test
  void shouldRejectCancelForWrongAccount() {
    UUID orderId = UUID.randomUUID();
    orderService.createOrder(
        new CreateOrderCommand(
            orderId, accountId, "BTCUSDT", OrderSide.BUY, OrderType.MARKET,
            new BigDecimal("0.5"), null, "client-4", "corr-8", null));

    UUID wrongAccountId = UUID.randomUUID();
    assertThrows(
        OrderDomainException.class,
        () ->
            orderService.cancelOrder(
                new CancelOrderCommand(orderId, wrongAccountId, "hacker", "corr-9", null)));
  }

  private UUID createAccountWithBalance(String asset, BigDecimal available) {
    UUID userId = UUID.randomUUID();
    UUID createdAccountId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', NOW(), NOW())",
        userId,
        "cancel-it@example.com");
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
