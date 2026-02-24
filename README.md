# Trading Platform Maven Skeleton

This repository contains a Maven multi-module skeleton for a trading platform.

## Coordinates

- Group: `com.tradingplatform`
- Artifact: `trading-platform`
- Version: `0.1.0-SNAPSHOT`

## Module Layout

### Apps

- `apps/trading-api`
- `apps/streaming`
- `apps/worker-exec`

### Shared Modules

- `modules/domain-orders`
- `modules/domain-wallet`
- `modules/domain-ledger`
- `modules/domain-risk`
- `modules/domain-instruments`
- `modules/domain-admin`
- `modules/integration-binance`
- `modules/infra`
- `modules/infra-kafka`

## Kafka Contracts (MVP)

Topic naming convention:

- `<domain>.<action>.v<major>`

Configured topics:

- `orders.submitted.v1` (key: `orderId`)
- `orders.updated.v1` (key: `orderId`)
- `executions.recorded.v1` (key: `orderId`)
- `balances.updated.v1` (key: `accountId`)
- `*.dlq.v1` variants for dead-letter handling

Event payload contracts:

- `OrderSubmittedV1`
- `OrderUpdatedV1`
- `ExecutionRecordedV1`
- `BalanceUpdatedV1`

Shared envelope:

- `EventEnvelope<T>` with `eventType`, `eventVersion`, `occurredAt`, `correlationId`, and business key.

## Build

```bash
mvn validate
mvn -DskipTests compile
```
