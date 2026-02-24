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

## Build

```bash
mvn validate
mvn -DskipTests compile
```
