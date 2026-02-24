package com.tradingplatform.tradingapi.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.domain.orders.OrderSide;
import com.tradingplatform.domain.orders.OrderType;
import com.tradingplatform.tradingapi.audit.JdbcAuditLogRepository;
import com.tradingplatform.tradingapi.risk.AccountLimitService;
import com.tradingplatform.tradingapi.risk.JdbcRiskCheckService;
import com.tradingplatform.tradingapi.risk.RiskViolationException;
import com.tradingplatform.tradingapi.risk.TradingControlService;
import com.tradingplatform.tradingapi.wallet.JdbcWalletRepository;
import com.tradingplatform.tradingapi.wallet.WalletReservationService;
import java.math.BigDecimal;
import java.time.Instant;
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
class RiskAuditedOrderCreateIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading");

  private JdbcTemplate jdbcTemplate;
  private OrderCreateUseCase auditedUseCase;
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

    OrderApplicationService orderApplicationService =
        new OrderApplicationService(
            new JdbcOrderRepository(jdbcTemplate),
            new JdbcOrderEventRepository(jdbcTemplate),
            new JdbcOutboxAppendRepository(jdbcTemplate, objectMapper),
            new WalletReservationService(new JdbcWalletRepository(jdbcTemplate)),
            new TradingControlService(jdbcTemplate),
            new AccountLimitService(jdbcTemplate),
            objectMapper);
    OrderCreateUseCase coreUseCase = new CoreOrderCreateUseCase(orderApplicationService);
    OrderCreateUseCase riskUseCase =
        new RiskValidatedOrderCreateUseCase(coreUseCase, new JdbcRiskCheckService(jdbcTemplate));
    auditedUseCase = new AuditedOrderCreateUseCase(riskUseCase, new JdbcAuditLogRepository(jdbcTemplate), objectMapper);

    accountId = createAccount();
    seedInstrumentAndLimits();
  }

  @Test
  void shouldAcceptWithinRiskAndWriteSuccessAudit() {
    auditedUseCase.create(
        new CreateOrderCommand(
            UUID.randomUUID(),
            accountId,
            "BTCUSDT",
            OrderSide.BUY,
            OrderType.LIMIT,
            new BigDecimal("1"),
            new BigDecimal("50200"),
            "client-ok",
            "corr-ok",
            Instant.now()));

    assertEquals(1, count("SELECT COUNT(*) FROM orders"));
    Map<String, Object> row =
        jdbcTemplate.queryForMap("SELECT result, action FROM audit_log ORDER BY created_at DESC LIMIT 1");
    assertEquals("SUCCESS", row.get("result"));
    assertEquals("ORDER_SUBMIT", row.get("action"));
  }

  @Test
  void shouldRejectByPriceBandAndWriteRejectedAudit() {
    assertThrows(
        RiskViolationException.class,
        () ->
            auditedUseCase.create(
                new CreateOrderCommand(
                    UUID.randomUUID(),
                    accountId,
                    "BTCUSDT",
                    OrderSide.BUY,
                    OrderType.LIMIT,
                    new BigDecimal("1"),
                    new BigDecimal("65000"),
                    "client-bad",
                    "corr-bad",
                    Instant.now())));

    assertEquals(0, count("SELECT COUNT(*) FROM orders"));
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT result, error_code FROM audit_log ORDER BY created_at DESC LIMIT 1");
    assertEquals("REJECTED", row.get("result"));
    assertEquals("PRICE_BAND_EXCEEDED", row.get("error_code"));
  }

  private UUID createAccount() {
    UUID userId = UUID.randomUUID();
    UUID createdAccountId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', NOW(), NOW())",
        userId,
        "risk-audit-it@example.com");
    jdbcTemplate.update(
        """
        INSERT INTO accounts (id, user_id, status, kyc_status, created_at, updated_at)
        VALUES (?, ?, 'ACTIVE', 'VERIFIED', NOW(), NOW())
        """,
        createdAccountId,
        userId);
    return createdAccountId;
  }

  private void seedInstrumentAndLimits() {
    jdbcTemplate.update(
        """
        INSERT INTO instruments (id, symbol, status, reference_price, created_at, updated_at)
        VALUES (?, 'BTCUSDT', 'ACTIVE', 50000, NOW(), NOW())
        """,
        UUID.randomUUID());
    jdbcTemplate.update(
        """
        INSERT INTO account_limits (account_id, max_order_notional, price_band_bps, created_at, updated_at)
        VALUES (?, 100000, 1000, NOW(), NOW())
        """,
        accountId);
  }

  private int count(String sql) {
    Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
    return value == null ? 0 : value;
  }
}
