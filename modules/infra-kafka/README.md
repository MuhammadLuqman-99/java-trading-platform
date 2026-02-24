# infra-kafka

Shared Kafka event contracts and wiring for trading-platform modules.

## Topics

- `orders.submitted.v1`
- `orders.updated.v1`
- `executions.recorded.v1`
- `balances.updated.v1`
- `orders.submitted.dlq.v1`
- `orders.updated.dlq.v1`
- `executions.recorded.dlq.v1`
- `balances.updated.dlq.v1`

Topic provisioning is managed by `infra-kafka` auto-configuration via `KafkaAdmin.NewTopics`
when `infra.kafka.topics.enabled=true`.

## Contracts

- Envelope: `EventEnvelope<T>`
- Types: `EventTypes`
- Headers: `EventHeaders`
- Payloads:
  - `OrderSubmittedV1`
  - `OrderUpdatedV1`
  - `ExecutionRecordedV1`
  - `BalanceUpdatedV1`

## Contract Tests

- Payload JSON schemas are stored under:
  `src/test/resources/contracts/payload-schemas/`
- Contract tests validate that payload JSON examples conform to these schemas and fail on
  missing required fields / invalid types.
- Any payload contract shape change must update both the schema file and
  `PayloadJsonSchemaContractTest`.

## Main Components

- `InfraKafkaAutoConfiguration`: producer/consumer factories and template beans.
- `EventPublisher` / `KafkaEventPublisher`: typed envelope publish with required headers.
- `EventConsumerAdapter<T>`: decode, validate, dispatch to handler, retry/DLQ hooks.
- `TopicNameValidator`: enforces `<domain>.<action>.v<major>` naming.

## Configuration

```yaml
infra:
  kafka:
    bootstrap-servers:
      - localhost:9092
    producer:
      client-id: trading-platform-producer
      acks: all
      idempotence-enabled: true
      retries: 3
      compression-type: lz4
      linger-ms: 5
      batch-size: 32768
      delivery-timeout-ms: 120000
      request-timeout-ms: 30000
      max-in-flight-requests-per-connection: 5
      send-timeout-ms: 0
    consumer:
      group-id: cg-default
      auto-offset-reset: earliest
      enable-auto-commit: false
      max-poll-records: 500
      max-poll-interval-ms: 300000
      session-timeout-ms: 10000
      heartbeat-interval-ms: 3000
      fetch-min-bytes: 1
      fetch-max-wait-ms: 500
      concurrency: 1
    retry:
      mode: fixed # fixed | exponential
      max-attempts: 1
      fixed-backoff-ms: 0
      initial-backoff-ms: 100
      max-backoff-ms: 10000
      multiplier: 2.0
      retryable-exceptions: []
    topics:
      enabled: true
      partitions: 3
      replication-factor: 1
```
