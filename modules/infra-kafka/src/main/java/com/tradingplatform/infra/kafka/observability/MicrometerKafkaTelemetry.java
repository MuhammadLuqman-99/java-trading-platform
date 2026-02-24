package com.tradingplatform.infra.kafka.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

public class MicrometerKafkaTelemetry implements KafkaTelemetry {
  private final MeterRegistry meterRegistry;

  public MicrometerKafkaTelemetry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void onPublishSuccess(String topic, String key, String eventType, long durationNanos) {
    Counter.builder("infra.kafka.publish.total")
        .description("Total Kafka publish attempts by outcome")
        .tag("topic", safeValue(topic))
        .tag("event_type", safeValue(eventType))
        .tag("outcome", "success")
        .register(meterRegistry)
        .increment();

    Timer.builder("infra.kafka.publish.duration")
        .description("Kafka publish latency")
        .tag("topic", safeValue(topic))
        .tag("event_type", safeValue(eventType))
        .register(meterRegistry)
        .record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
  }

  @Override
  public void onPublishFailure(String topic, String key, String eventType, Throwable error) {
    Counter.builder("infra.kafka.publish.total")
        .description("Total Kafka publish attempts by outcome")
        .tag("topic", safeValue(topic))
        .tag("event_type", safeValue(eventType))
        .tag("outcome", "failure")
        .tag("error", safeError(error))
        .register(meterRegistry)
        .increment();
  }

  @Override
  public void onConsumeSuccess(
      String topic, String key, String eventType, int partition, long offset, long durationNanos) {
    Counter.builder("infra.kafka.consume.total")
        .description("Total Kafka consume attempts by outcome")
        .tag("topic", safeValue(topic))
        .tag("event_type", safeValue(eventType))
        .tag("outcome", "success")
        .tag("partition", Integer.toString(Math.max(0, partition)))
        .register(meterRegistry)
        .increment();

    Timer.builder("infra.kafka.consume.duration")
        .description("Kafka consume processing latency")
        .tag("topic", safeValue(topic))
        .tag("event_type", safeValue(eventType))
        .register(meterRegistry)
        .record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
  }

  @Override
  public void onConsumeFailure(String topic, String key, String eventType, Throwable error) {
    Counter.builder("infra.kafka.consume.total")
        .description("Total Kafka consume attempts by outcome")
        .tag("topic", safeValue(topic))
        .tag("event_type", safeValue(eventType))
        .tag("outcome", "failure")
        .tag("error", safeError(error))
        .register(meterRegistry)
        .increment();
  }

  @Override
  public void onDeadLetter(String topic, String key, Throwable error) {
    Counter.builder("infra.kafka.deadletter.total")
        .description("Total dead-letter events published")
        .tag("topic", safeValue(topic))
        .tag("error", safeError(error))
        .register(meterRegistry)
        .increment();
  }

  private static String safeValue(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    return value;
  }

  private static String safeError(Throwable error) {
    if (error == null) {
      return "none";
    }
    return error.getClass().getSimpleName();
  }
}
