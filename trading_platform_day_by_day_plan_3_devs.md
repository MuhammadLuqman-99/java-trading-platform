# Trading Platform Day-by-Day Plan (3 Devs)

> Based on **SPEC-1**: brokerage-style crypto trading platform, **Binance Spot**, **omnibus**, **spot-only**, **simulated deposits**, **API-only**, **single VM (Docker Compose)**, **Kafka + outbox**, **Maven multi-module**.

## Assumptions
- 8 weeks × 5 working days = **40 dev-days per developer** (total 120 person-days).
- 1 shared `main` branch with PR reviews (min 1 reviewer).
- Daily: 15m standup + 60–90m integration window.

## Developer lanes
- **Dev A (Core Domain & REST API):** orders, wallet, risk, admin APIs, OpenAPI annotations.
- **Dev B (Eventing & Binance Worker):** outbox publisher, Kafka consumers, Binance REST + WS user stream, execution processing.
- **Dev C (Infra/Security/Quality):** docker-compose, reverse proxy/TLS, Keycloak realm, observability, CI, testcontainers, performance + hardening.

## Definition of done (per day)
- Code merged to `main` + passes CI + basic smoke test documented.

---

## Week 1 — Foundation (Days 1–5) ✅

| Day | Dev A | Dev B | Dev C | Output | Status |
|---|---|---|---|---|---|
| 1 | Create Maven parent + module skeletons (`apps/*`, `modules/*`) | Define event contracts + topic names; create `infra-kafka` module skeleton | Create `deploy/docker-compose.yml` (postgres/redis/kafka/keycloak) | Repo builds locally; `docker-compose up` works | ✅ Done |
| 2 | Add Spring Boot app skeleton for `trading-api` (health, version) | Add outbox table migration stub + outbox publisher skeleton service | Wire CI pipeline (build + unit tests) + code style (spotless/checkstyle optional) | CI green; apps start and expose `/actuator/health` | ✅ Done |
| 3 | Add `springdoc-openapi` + basic API groups | Define Kafka client config + producer/consumer base + retry policy | Keycloak realm export (roles, clients) + JWT config notes | Swagger UI visible; Keycloak realm file in repo | ✅ Done |
| 4 | Implement auth guards (role-based) for a sample endpoint | Implement idempotency-key middleware skeleton + persistence API | Observability baseline: JSON logs + tracing/metrics placeholders | Auth flow works (JWT validates); logs structured | ✅ Done |
| 5 | Define domain package structure + error model (RFC7807 style) | Create base “worker” app module (no logic yet) | Add local dev runbook + env var conventions | Docs: how to run, how to auth, how to view Swagger | ✅ Done |

---

## Week 2 — Data model + invariants (Days 6–10) ✅

| Day | Dev A | Dev B | Dev C | Output | Status |
|---|---|---|---|---|---|
| 6 | Flyway migrations: users/accounts/wallet_balances/reservations | Flyway migrations: outbox_events + idempotency_keys | Add Testcontainers base + integration test harness | DB schema boots; first integration test runs | ✅ Done |
| 7 | Implement Wallet reservation service (`SELECT FOR UPDATE`) | Implement outbox publisher loop (poll→publish→mark) | Add Redis (rate limit counters) + config | Funds reservation invariant tested | ✅ Done |
| 8 | Implement Order entity + state machine + order_events append | Create Kafka topics in compose init (or auto-create) + producer utils | Add contract tests for event payload JSON schema | Order transitions + events recorded | ✅ Done |
| 9 | Implement `POST /v1/orders` (market/limit) with idempotency | Consumer skeleton: `orders.submitted` → calls adapter interface | Keycloak role mapping + endpoint security tests | Order API returns 202; duplicates dedupe | ✅ Done |
| 10 | Implement `POST /v1/orders/{id}/cancel` + order queries | Implement wallet reservation release on cancel (local) | Add DB indexes + basic perf checks (explain plans) | Cancel + list orders works locally | ✅ Done |

---

## Week 3 — Risk + ledger core + simulated funding (Days 11–15) ✅

| Day | Dev A | Dev B | Dev C | Output | Status |
|---|---|---|---|---|---|
| 11 | Implement Risk checks: max notional, price band, instrument status | Define ledger transaction + entries tables + repository | Add audit_log table + standard audit decorator | Risk rejects/accepts correctly; audited | ✅ Done |
| 12 | Implement Admin API: set limits, freeze/unfreeze trading | Implement ledger posting logic for simulated credit/debit | Add admin-only auth tests; secure admin routes | Admin can configure limits & freeze | ✅ Done |
| 13 | Implement Admin API: manual credit/debit (simulated deposit) | Emit `balances.updated` events on ledger changes | Add Postman collection generation step in CI | Funding simulation works + events emitted | ✅ Done |
| 14 | Implement portfolio endpoints: balances + positions (derived) | Implement reconciliation “stubs” (interfaces + scheduled job scaffold) | Add smoke test script (curl/postman) in repo | Portfolio APIs return correct values | ✅ Done |
| 15 | Harden error handling + idempotency edge cases | Add dead-letter strategy (retry topics or DB table) skeleton | Security checklist doc (secrets, logging, headers) | Stable API behavior + docs | ✅ Done |

---

## Week 4 — Binance REST integration (Days 16–20) ✅

| Day | Dev A | Dev B | Dev C | Output | Status |
|---|---|---|---|---|---|
| 16 | Add instruments config endpoints (curated list) | Implement Binance REST client (signed requests) | Secrets handling in compose (env + file mounts) | REST client can call public + signed endpoints | ✅ Done |
| 17 | Enforce precision/filters in order validation | Implement submit order: `newClientOrderId = order_id` mapping | Add connector integration test scaffolding (mock server) | Orders can be submitted to Binance test env | ✅ Done |
| 18 | Add order status mapping table + domain mapping | Implement cancel/query order REST + status mapping | Rate-limit handling utilities (backoff, jitter) | Cancel/query works; backoff in place | ✅ Done |
| 19 | Add “exchange ids” fields to orders + migrations if needed | Implement worker consume `orders.submitted` → place order → update status ACK | Add DB transaction boundaries + retry safety review | End-to-end: API→Kafka→worker→DB update | ✅ Done |
| 20 | Add admin endpoint: connector health + last error | Implement “catch-up” REST polling (open orders, recent trades) | Observability: metrics counters for connector errors/latency | Basic connector health telemetry | ✅ Done |

---

## Week 5 — Binance WS user stream + fills (Days 21–25)

| Day | Dev A | Dev B | Dev C | Output |
|---|---|---|---|---|
| 21 | Finalize execution/event domain objects | Implement WS client + resilient reconnect loop | Add chaos tests: kill worker container + recover | WS connects and stays connected |
| 22 | Implement execution ingestion pipeline interfaces | Parse `executionReport` → upsert executions + update order filled_qty/status | Add dedupe constraints for exchange_trade_id | Fills recorded exactly-once (within system) | ✅ Done |
| 23 | Implement ledger posting for real fills (quote/base/fees) | On fill: ledger entries + release reservations + update balances/positions | Add integration test: simulated fill event → ledger invariant | Balances/positions correct after fills |
| 24 | Add endpoints: trade history/executions query | Publish `orders.updated` + `balances.updated` on fill updates | Add dashboards basics (metrics names + docs) | Query executions works; events flow |
| 25 | Add admin endpoint: replay/catch-up trigger | Implement reconnect catch-up: open orders + recent trades reconcile | Add alert thresholds (log-based rules) notes | Worker resilient + catch-up functional | ✅ Done |

---

## Week 6 — Real-time updates + admin/compliance MVP (Days 26–30)

| Day | Dev A | Dev B | Dev C | Output |
|---|---|---|---|---|
| 26 | Add Streaming app endpoints (SSE/WS) skeleton | Streaming consumer: consume updates topics → push to sessions | Reverse proxy routes (API + streaming) | Clients can subscribe and receive test events |
| 27 | Secure streaming endpoints (JWT, per-account scope) | Implement session routing (by account_id) | Load test streaming baseline (light) | Real-time order updates to client |
| 28 | Compliance role endpoints: audit log query + filters | Add event “envelope” standard (correlation_id, timestamps) | Add OpenAPI linting + versioning in CI | Compliance APIs documented |
| 29 | Freeze/unfreeze: ensure enforced in order placement | Worker respects global freeze + returns controlled errors | Add operational runbook: freeze workflow | Freeze blocks trading reliably |
| 30 | API polish: pagination, sorting, consistent responses | Improve retry/DLQ handling + admin endpoints for DLQ | Add security headers (proxy) + CORS policy | MVP-grade API ergonomics |

---

## Week 7 — Reconciliation + circuit breakers + performance (Days 31–35)

| Day | Dev A | Dev B | Dev C | Output |
|---|---|---|---|---|
| 31 | Implement reconciliation data model (snapshots, drift) | Implement balance reconciliation: Binance balances vs internal totals | Add scheduled job framework + lock (single runner) | Drift metric computed |
| 32 | Implement circuit breaker: auto-pause on drift threshold | Implement open-orders reconciliation + missing execution detection | Add alerting hooks (log patterns/metrics thresholds) | Auto-pause triggers correctly |
| 33 | Add admin workflow: acknowledge + resume trading | Implement remediation tools: resync balances, replay | Load test (orders) + DB/Kafka tuning notes | Resume flow documented + tested |
| 34 | Add “read model” optimizations (indexes, query tuning) | Optimize worker concurrency (partition strategy, batching) | JVM tuning baseline (heap, GC) for VM | Performance baseline achieved |
| 35 | Stabilization: bug fixes + edge cases | Stabilization: reconnect storms, dedupe, error paths | Security review checklist + secrets rotation doc | Release candidate tagged |

---

## Week 8 — Single VM production readiness + handover (Days 36–40)

| Day | Dev A | Dev B | Dev C | Output |
|---|---|---|---|---|
| 36 | Finalize OpenAPI tags + examples | Finalize worker run modes + graceful shutdown | Production docker-compose (profiles, volumes, healthchecks) | Prod compose boots cleanly |
| 37 | Add Postman collection + env files + auth examples | Add runbook: connector incident playbook | TLS plan: Caddy or Nginx+certbot; firewall notes | Ops docs ready |
| 38 | Add release notes + migration guide | Add backup/restore procedures for ledger integrity | Implement backups (pg_dump) + restore drill checklist | Backup strategy validated |
| 39 | End-to-end UAT checklist + sign-off | End-to-end test run with Binance test env | Observability: dashboards + SLOs defined | UAT pass report |
| 40 | Handover: architecture + API + ops training | Handover: connector + reconciliation training | Handover: infra + security training | Contractor handover complete |

---

## Critical path notes
- **Wallet reservation + idempotency + outbox** (Weeks 2–3) must be correct before Binance goes live.
- **WS executionReport ingestion + dedupe + ledger posting** (Week 5) is the core “money correctness” chain.
- **Reconciliation + circuit breaker** (Week 7) is required for omnibus safety.

## Optional scope cuts (if time tight)
- Move streaming (Week 6) to post-MVP: keep API polling only.
- Simplify reconciliation to balances-only (skip open-order reconciliation) for MVP.

