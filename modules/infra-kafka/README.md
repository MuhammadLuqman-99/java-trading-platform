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

## Contracts

- Envelope: `EventEnvelope<T>`
- Types: `EventTypes`
- Headers: `EventHeaders`
- Payloads:
  - `OrderSubmittedV1`
  - `OrderUpdatedV1`
  - `ExecutionRecordedV1`
  - `BalanceUpdatedV1`

## Main Components

- `InfraKafkaAutoConfiguration`: producer/consumer factories and template beans.
- `EventPublisher` / `KafkaEventPublisher`: typed envelope publish with required headers.
- `EventConsumerAdapter<T>`: decode, validate, dispatch to handler, retry/DLQ hooks.
- `TopicNameValidator`: enforces `<domain>.<action>.v<major>` naming.
