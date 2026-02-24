package com.tradingplatform.infra.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "infra.kafka")
public class InfraKafkaProperties {
    private List<String> bootstrapServers = new ArrayList<>(List.of("localhost:9092"));
    private String producerClientId = "trading-platform-producer";
    private String consumerGroupId = "cg-default";
    private String autoOffsetReset = "earliest";
    private int producerRetries = 3;
    private boolean producerIdempotenceEnabled = true;

    public List<String> getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(List<String> bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getProducerClientId() {
        return producerClientId;
    }

    public void setProducerClientId(String producerClientId) {
        this.producerClientId = producerClientId;
    }

    public String getConsumerGroupId() {
        return consumerGroupId;
    }

    public void setConsumerGroupId(String consumerGroupId) {
        this.consumerGroupId = consumerGroupId;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    public void setAutoOffsetReset(String autoOffsetReset) {
        this.autoOffsetReset = autoOffsetReset;
    }

    public int getProducerRetries() {
        return producerRetries;
    }

    public void setProducerRetries(int producerRetries) {
        this.producerRetries = producerRetries;
    }

    public boolean isProducerIdempotenceEnabled() {
        return producerIdempotenceEnabled;
    }

    public void setProducerIdempotenceEnabled(boolean producerIdempotenceEnabled) {
        this.producerIdempotenceEnabled = producerIdempotenceEnabled;
    }

    public String bootstrapServersAsCsv() {
        return String.join(",", bootstrapServers);
    }
}
