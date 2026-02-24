# Security Checklist (MVP)

This checklist is for local/dev and CI hardening of `trading-api` and `worker-exec`.

## 1. Secrets

- Use environment variables for secrets (`SPRING_DATASOURCE_PASSWORD`, `OAUTH2_JWK_SET_URI`, client secrets).
- Do not commit tokens, credentials, or `.env` files with real secrets.
- Rotate compromised credentials immediately and restart affected services.
- Keep local defaults only for non-production development.

## 2. Logging Hygiene

- Never log Authorization headers or JWT access tokens.
- Avoid logging full request bodies for sensitive endpoints.
- Keep structured JSON logs enabled and monitor `WARN`/`ERROR` spikes.
- Log exception class + stable error code, not full sensitive payload content.

## 3. HTTP/API Security Headers

- Verify `X-Content-Type-Options: nosniff` is present.
- Verify `X-Frame-Options: DENY` is present.
- Verify cache-control headers for error responses are restrictive.
- Ensure unauthenticated endpoints remain explicitly scoped (`/v1/version`, health, docs).

## 4. AuthN/AuthZ

- JWT resource server is enabled and JWKS URI is configured.
- Admin endpoints must enforce `ROLE_ADMIN`.
- Trading endpoints must enforce `ROLE_TRADER`.
- Add tests for unauthorized (`401`) and forbidden (`403`) on sensitive routes.

## 5. Idempotency and Replay Safety

- Keep idempotency enabled for write endpoints (`/v1/orders/**`).
- Require `Idempotency-Key` and reject missing/mismatched/in-progress/expired requests.
- Ensure idempotent replay returns stored status/body only for completed non-expired records.

## 6. Kafka / DLQ

- Keep producer idempotence enabled.
- Ensure dead-letter publishing is configured (`infra.kafka.dead-letter.*`).
- Verify DLQ topics exist and receive irrecoverable failures.

## 7. Verification Commands

```bash
# Build + tests
mvn -B -ntp verify

# Security-focused API test
mvn -pl apps/trading-api -am "-Dtest=TradingApiSecurityTest" "-Dsurefire.failIfNoSpecifiedTests=false" test

# Infra-kafka config/tests
mvn -pl modules/infra-kafka -am test
```
