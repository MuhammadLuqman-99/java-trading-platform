package com.tradingplatform.infra.kafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.infra.kafka.errors.DeadLetterPublisher;
import com.tradingplatform.infra.kafka.errors.FixedBackoffRetryPolicy;
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
  public RetryPolicy retryPolicy() {
    return new FixedBackoffRetryPolicy(1, Duration.ZERO);
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
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServersAsCsv());
    config.put(ProducerConfig.CLIENT_ID_CONFIG, properties.getProducerClientId());
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, properties.isProducerIdempotenceEnabled());
    config.put(ProducerConfig.RETRIES_CONFIG, properties.getProducerRetries());
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
      KafkaTelemetry kafkaTelemetry) {
    return new KafkaEventPublisher(infraKafkaTemplate, eventEnvelopeJsonCodec, kafkaTelemetry);
  }

  @Bean
  @ConditionalOnMissingBean(name = "infraKafkaConsumerFactory")
  public ConsumerFactory<String, String> infraKafkaConsumerFactory(
      InfraKafkaProperties properties) {
    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServersAsCsv());
    config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getConsumerGroupId());
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.getAutoOffsetReset());
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(config);
  }

  @Bean(name = "infraKafkaListenerContainerFactory")
  @ConditionalOnMissingBean(name = "infraKafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, String> infraKafkaListenerContainerFactory(
      ConsumerFactory<String, String> infraKafkaConsumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(infraKafkaConsumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    return factory;
  }
}
