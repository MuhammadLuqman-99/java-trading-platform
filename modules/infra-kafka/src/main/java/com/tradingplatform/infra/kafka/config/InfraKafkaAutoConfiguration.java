package com.tradingplatform.infra.kafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.infra.kafka.errors.DeadLetterPublisher;
import com.tradingplatform.infra.kafka.errors.LoggingDeadLetterPublisher;
import com.tradingplatform.infra.kafka.errors.RetryPolicy;
import com.tradingplatform.infra.kafka.observability.KafkaTelemetry;
import com.tradingplatform.infra.kafka.observability.NoOpKafkaTelemetry;
import com.tradingplatform.infra.kafka.producer.EventPublisher;
import com.tradingplatform.infra.kafka.producer.KafkaEventPublisher;
import com.tradingplatform.infra.kafka.serde.EventEnvelopeJsonCodec;
import com.tradingplatform.infra.kafka.serde.EventObjectMapperFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@AutoConfiguration
@EnableConfigurationProperties(InfraKafkaProperties.class)
public class InfraKafkaAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(name = "kafkaEventObjectMapper")
  public ObjectMapper kafkaEventObjectMapper() {
    return EventObjectMapperFactory.create();
  }

  @Bean
  @ConditionalOnMissingBean
  public EventEnvelopeJsonCodec eventEnvelopeJsonCodec(
      @Qualifier("kafkaEventObjectMapper") ObjectMapper kafkaEventObjectMapper) {
    return new EventEnvelopeJsonCodec(kafkaEventObjectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public KafkaTelemetry kafkaTelemetry() {
    return new NoOpKafkaTelemetry();
  }

  @Bean
  @ConditionalOnMissingBean
  public RetryPolicy retryPolicy(InfraKafkaProperties properties) {
    return RetryPolicyFactory.create(properties.getRetry());
  }

  @Bean
  @ConditionalOnMissingBean
  public DeadLetterPublisher deadLetterPublisher() {
    return new LoggingDeadLetterPublisher();
  }

  @Bean
  @ConditionalOnMissingBean(name = "infraKafkaProducerFactory")
  public ProducerFactory<String, String> infraKafkaProducerFactory(
      InfraKafkaProperties properties) {
    InfraKafkaProperties.Producer producer = properties.getProducer();

    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServersAsCsv());
    config.put(ProducerConfig.CLIENT_ID_CONFIG, properties.effectiveProducerClientId());
    config.put(ProducerConfig.ACKS_CONFIG, producer.getAcks());
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, properties.effectiveProducerIdempotenceEnabled());
    config.put(ProducerConfig.RETRIES_CONFIG, properties.effectiveProducerRetries());
    config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, producer.getCompressionType());
    config.put(ProducerConfig.LINGER_MS_CONFIG, producer.getLingerMs());
    config.put(ProducerConfig.BATCH_SIZE_CONFIG, producer.getBatchSize());
    config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, producer.getDeliveryTimeoutMs());
    config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, producer.getRequestTimeoutMs());
    config.put(
        ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
        resolveMaxInFlightRequests(properties));
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new DefaultKafkaProducerFactory<>(config);
  }

  @Bean
  @ConditionalOnMissingBean(name = "infraKafkaTemplate")
  public KafkaTemplate<String, String> infraKafkaTemplate(
      ProducerFactory<String, String> infraKafkaProducerFactory) {
    return new KafkaTemplate<>(infraKafkaProducerFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  public EventPublisher eventPublisher(
      KafkaTemplate<String, String> infraKafkaTemplate,
      EventEnvelopeJsonCodec eventEnvelopeJsonCodec,
      KafkaTelemetry kafkaTelemetry,
      InfraKafkaProperties properties) {
    long sendTimeoutMs = Math.max(0L, properties.getProducer().getSendTimeoutMs());
    return new KafkaEventPublisher(
        infraKafkaTemplate,
        eventEnvelopeJsonCodec,
        kafkaTelemetry,
        Duration.ofMillis(sendTimeoutMs));
  }

  @Bean
  @ConditionalOnMissingBean(name = "infraKafkaConsumerFactory")
  public ConsumerFactory<String, String> infraKafkaConsumerFactory(
      InfraKafkaProperties properties) {
    InfraKafkaProperties.Consumer consumer = properties.getConsumer();

    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServersAsCsv());
    config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.effectiveConsumerGroupId());
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.effectiveAutoOffsetReset());
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, consumer.isEnableAutoCommit());
    config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumer.getMaxPollRecords());
    config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, consumer.getMaxPollIntervalMs());
    config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, consumer.getSessionTimeoutMs());
    config.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, consumer.getHeartbeatIntervalMs());
    config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, consumer.getFetchMinBytes());
    config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, consumer.getFetchMaxWaitMs());
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(config);
  }

  @Bean(name = "infraKafkaListenerContainerFactory")
  @ConditionalOnMissingBean(name = "infraKafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, String> infraKafkaListenerContainerFactory(
      ConsumerFactory<String, String> infraKafkaConsumerFactory, InfraKafkaProperties properties) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(infraKafkaConsumerFactory);
    factory.setConcurrency(Math.max(1, properties.getConsumer().getConcurrency()));
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    return factory;
  }

  private int resolveMaxInFlightRequests(InfraKafkaProperties properties) {
    int configuredMax = Math.max(1, properties.getProducer().getMaxInFlightRequestsPerConnection());
    if (properties.effectiveProducerIdempotenceEnabled()) {
      return Math.min(5, configuredMax);
    }
    return configuredMax;
  }
}
