package com.tradingplatform.worker.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.orders.OrderSide;
import com.tradingplatform.domain.orders.OrderType;
import com.tradingplatform.infra.kafka.errors.FixedBackoffRetryPolicy;
import com.tradingplatform.infra.kafka.errors.LoggingDeadLetterPublisher;
import com.tradingplatform.infra.kafka.observability.NoOpKafkaTelemetry;
import com.tradingplatform.infra.kafka.producer.EventPublisher;
import com.tradingplatform.infra.kafka.producer.KafkaEventPublisher;
import com.tradingplatform.infra.kafka.serde.EventEnvelopeJsonCodec;
import com.tradingplatform.infra.kafka.serde.EventObjectMapperFactory;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import com.tradingplatform.tradingapi.orders.CreateOrderCommand;
import com.tradingplatform.tradingapi.orders.JdbcOrderEventRepository;
import com.tradingplatform.tradingapi.orders.JdbcOrderRepository;
import com.tradingplatform.tradingapi.orders.JdbcOutboxAppendRepository;
import com.tradingplatform.tradingapi.orders.OrderApplicationService;
import com.tradingplatform.tradingapi.risk.AccountLimitService;
import com.tradingplatform.tradingapi.risk.TradingControlService;
import com.tradingplatform.tradingapi.wallet.JdbcWalletRepository;
import com.tradingplatform.tradingapi.wallet.WalletReservationService;
import com.tradingplatform.worker.consumer.OrderSubmissionProcessor;
import com.tradingplatform.worker.consumer.OrderSubmittedConsumer;
import com.tradingplatform.worker.execution.ExecutionAckResult;
import com.tradingplatform.worker.outbox.JdbcOutboxRepository;
import com.tradingplatform.worker.outbox.OutboxPublisherProperties;
import com.tradingplatform.worker.outbox.OutboxPublisherService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class ApiKafkaWorkerDbUpdateIT {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trading")
          .withPassword("trading");

  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.0"));

  private JdbcTemplate jdbcTemplate;
  private OrderApplicationService orderApplicationService;
  private OutboxPublisherService outboxPublisherService;
  private OrderSubmittedConsumer orderSubmittedConsumer;
  private DefaultKafkaProducerFactory<String, String> producerFactory;
  private KafkaConsumer<String, String> submittedTopicConsumer;

  @BeforeEach
  void setUp() throws Exception {
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
    ObjectMapper objectMapper = EventObjectMapperFactory.create();

    orderApplicationService =
        new OrderApplicationService(
            new JdbcOrderRepository(jdbcTemplate),
            new JdbcOrderEventRepository(jdbcTemplate),
            new JdbcOutboxAppendRepository(jdbcTemplate, objectMapper),
            new WalletReservationService(new JdbcWalletRepository(jdbcTemplate)),
            new TradingControlService(jdbcTemplate),
            new AccountLimitService(jdbcTemplate),
            objectMapper);

    createTopics();

    Map<String, Object> producerConfig =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ProducerConfig.CLIENT_ID_CONFIG, "api-kafka-worker-db-e2e",
            ProducerConfig.ACKS_CONFIG, "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    producerFactory = new DefaultKafkaProducerFactory<>(producerConfig);
    KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory);
    EventEnvelopeJsonCodec codec = new EventEnvelopeJsonCodec(EventObjectMapperFactory.create());
    EventPublisher eventPublisher =
        new KafkaEventPublisher(kafkaTemplate, codec, new NoOpKafkaTelemetry(), Duration.ofSeconds(5));

    OutboxPublisherProperties outboxProperties = new OutboxPublisherProperties();
    outboxProperties.setBatchSize(100);
    outboxProperties.setProducerName("api-kafka-worker-db-e2e-outbox");
    outboxPublisherService =
        new OutboxPublisherService(
            new JdbcOutboxRepository(jdbcTemplate), eventPublisher, outboxProperties, objectMapper);

    OrderSubmissionProcessor processor =
        new OrderSubmissionProcessor(
            jdbcTemplate,
            objectMapper,
            command ->
                new ExecutionAckResult(
                    "BINANCE", "binance-" + command.orderId(), command.orderId()));
    orderSubmittedConsumer =
        new OrderSubmittedConsumer(
            codec,
            new LoggingDeadLetterPublisher(),
            new FixedBackoffRetryPolicy(1, Duration.ZERO),
            new NoOpKafkaTelemetry(),
            processor);

    Map<String, Object> consumerConfig =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "api-kafka-worker-db-e2e-submitted",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    submittedTopicConsumer = new KafkaConsumer<>(consumerConfig);
    submittedTopicConsumer.subscribe(List.of(TopicNames.ORDERS_SUBMITTED_V2));
  }

  @AfterEach
  void tearDown() {
    if (submittedTopicConsumer != null) {
      submittedTopicConsumer.close();
    }
    if (producerFactory != null) {
      producerFactory.destroy();
    }
  }

  @Test
  void shouldProcessCreateOrderFromApiThroughKafkaToWorkerDbAckUpdate() {
    UUID accountId = createAccount();
    UUID orderId = UUID.randomUUID();

    orderApplicationService.createOrder(
        new CreateOrderCommand(
            orderId,
            accountId,
            "BTCUSDT",
            OrderSide.BUY,
            OrderType.LIMIT,
            new BigDecimal("0.25"),
            new BigDecimal("42000.00"),
            "client-e2e-1",
            "corr-e2e-1",
            Instant.parse("2026-02-25T02:00:00Z")));

    assertEquals(
        2,
        queryCount(
            "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'OrderSubmitted' AND event_key = ?",
            orderId.toString()));

    outboxPublisherService.publishPendingEvents();

    ConsumerRecord<String, String> submittedRecord = pollSubmittedRecord(Duration.ofSeconds(15));
    assertNotNull(submittedRecord);

    Acknowledgment acknowledgment = mock(Acknowledgment.class);
    orderSubmittedConsumer.onMessage(submittedRecord, acknowledgment);

    assertEquals(
        "ACK", jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId));
    assertEquals(
        "BINANCE",
        jdbcTemplate.queryForObject("SELECT exchange_name FROM orders WHERE id = ?", String.class, orderId));
    assertEquals(
        orderId.toString(),
        jdbcTemplate.queryForObject(
            "SELECT exchange_client_order_id FROM orders WHERE id = ?", String.class, orderId));
    assertEquals(
        1, queryCount("SELECT COUNT(*) FROM processed_kafka_events WHERE order_id = ?", orderId));
    assertEquals(
        1,
        queryCount(
            "SELECT COUNT(*) FROM outbox_events WHERE topic = ? AND event_key = ?",
            TopicNames.ORDERS_UPDATED_V2,
            orderId.toString()));
  }

  private void createTopics() throws Exception {
    try (AdminClient admin =
        AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
      admin
          .createTopics(
              List.of(
                  new NewTopic(TopicNames.ORDERS_SUBMITTED_V1, 1, (short) 1),
                  new NewTopic(TopicNames.ORDERS_SUBMITTED_V2, 1, (short) 1),
                  new NewTopic(TopicNames.ORDERS_UPDATED_V1, 1, (short) 1),
                  new NewTopic(TopicNames.ORDERS_UPDATED_V2, 1, (short) 1)))
          .all()
          .get(20, TimeUnit.SECONDS);
    }
  }

  private ConsumerRecord<String, String> pollSubmittedRecord(Duration timeout) {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      var records = submittedTopicConsumer.poll(Duration.ofMillis(250));
      for (ConsumerRecord<String, String> record : records) {
        if (TopicNames.ORDERS_SUBMITTED_V2.equals(record.topic())) {
          return record;
        }
      }
    }
    fail("Timed out waiting for orders.submitted.v2 event");
    return null;
  }

  private UUID createAccount() {
    UUID userId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', NOW(), NOW())",
        userId,
        "api-kafka-worker-db-e2e@example.com");
    jdbcTemplate.update(
        """
        INSERT INTO accounts (id, user_id, status, kyc_status, created_at, updated_at)
        VALUES (?, ?, 'ACTIVE', 'VERIFIED', NOW(), NOW())
        """,
        accountId,
        userId);
    return accountId;
  }

  private int queryCount(String sql, Object... args) {
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
    return count == null ? 0 : count;
  }
}
