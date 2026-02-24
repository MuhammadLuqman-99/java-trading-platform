# Trading Platform Maven Skeleton

This repository contains a Maven multi-module skeleton for a trading platform.

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### 1. Start infrastructure

```bash
docker compose --env-file deploy/.env.example -f deploy/docker-compose.yml up -d
```

This starts PostgreSQL, Redis, Kafka, and Keycloak. Wait for all health checks to pass:

```bash
docker compose -f deploy/docker-compose.yml ps
```

### 2. Build the project

```bash
mvn -B -ntp clean verify
```

### 3. Run trading-api

```bash
mvn -pl apps/trading-api spring-boot:run
```

The API starts on port **8081**.

### 4. Run worker-exec

In a separate terminal:

```bash
mvn -pl apps/worker-exec spring-boot:run
```

The worker starts on port **8082**.

### 5. Verify services

```bash
# Health checks
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health

# Version
curl http://localhost:8081/v1/version
```

### 6. Access Swagger UI

Open in your browser: http://localhost:8081/swagger-ui.html

### 7. Get a JWT token and test auth

```bash
# Get a token from Keycloak (default dev credentials)
TOKEN=$(curl -s -X POST \
  http://localhost:8080/realms/trading/protocol/openid-connect/token \
  -d "grant_type=client_credentials" \
  -d "client_id=trading-api" \
  -d "client_secret=<your-client-secret>" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# Test admin endpoint (requires ADMIN role)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/v1/admin/ping
```

See `docs/security/jwt-config-notes.md` for full auth details.

### Environment Variables

All apps use `${VAR:default}` convention. See `deploy/.env.example` for infrastructure defaults.

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8081` / `8082` | App HTTP port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/trading` | PostgreSQL URL |
| `SPRING_DATASOURCE_USERNAME` | `trading` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `trading_pass` | DB password |
| `OAUTH2_JWK_SET_URI` | `http://localhost:8080/realms/trading/...` | Keycloak JWKS endpoint |
| `TRACING_ENABLED` | `true` | Enable distributed tracing |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | Trace sampling rate |

---

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

## Testing

```bash
# Unit tests only (*Test, via Surefire)
mvn test

# Unit + integration tests (*IT, via Failsafe + Testcontainers)
mvn verify
```

Integration tests require Docker to be running locally because they use Testcontainers
for PostgreSQL and Kafka.

## CI and Code Style

This repository uses GitHub Actions to enforce build, unit tests, and formatting checks on pull requests and pushes to `master`/`main`.

Commands equivalent to CI:

```bash
mvn -B -ntp spotless:check verify
```

Common local workflow:

```bash
mvn spotless:apply
mvn -B -ntp verify
```

## Keycloak Realm and JWT Notes

- Realm export file: `deploy/keycloak/realm-trading-platform-dev.json`
- Compose imports this realm on startup (`keycloak` runs with `--import-realm`)
- JWT integration notes: `docs/security/jwt-config-notes.md`

Boot local auth stack:

```bash
docker compose --env-file deploy/.env.example -f deploy/docker-compose.yml up -d keycloak postgres
```

## Observability Baseline

Baseline enabled for:

- `apps/trading-api`
- `apps/worker-exec`

Included:

- JSON console logging (`logback-spring.xml`)
- Trace/span placeholders in logs (`traceId`, `spanId`)
- Actuator endpoints: `health`, `info`, `prometheus`
- Kafka telemetry placeholders via `KafkaTelemetry` + Micrometer counters/timers

Useful local checks:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/prometheus
curl http://localhost:8082/actuator/health
curl http://localhost:8082/actuator/prometheus
```

Tracing placeholder environment variables:

- `TRACING_ENABLED` (default `true`)
- `TRACING_SAMPLING_PROBABILITY` (default `1.0`)
- `MANAGEMENT_OTLP_TRACING_ENDPOINT` (optional placeholder for OTLP exporter wiring)
