# Worker Fill Metrics Basics

This document defines baseline metrics and starter dashboard queries for fill processing in `worker-exec`.

## Metrics

### `worker.executions.processed.total`
- Type: Counter
- Tags:
  - `connector` (`binance-spot`)
  - `outcome` (`inserted`, `duplicate`, `unmapped`, `failed`)
- Meaning: Number of trade snapshots processed by the fill processor.

### `worker.executions.process.duration`
- Type: Timer
- Tags:
  - `connector`
- Meaning: End-to-end processing latency per trade snapshot.

### `worker.order_fill.updates.total`
- Type: Counter
- Tags:
  - `status` (`PARTIALLY_FILLED`, `FILLED`)
- Meaning: Number of order updates emitted due to fills.

### `worker.balance.updates.total`
- Type: Counter
- Tags:
  - `asset`
  - `outcome` (`success`, `failed`)
- Meaning: Number of balance mutation attempts caused by fills.

### `worker.outbox.append.total`
- Type: Counter
- Tags:
  - `event_type` (`ExecutionRecorded`, `OrderUpdated`, `BalanceUpdated`)
  - `outcome` (`success`, `failed`)
- Meaning: Number of outbox append attempts per event type.

## Starter Dashboard Panels (PromQL)

### Fill outcomes (5m rate)
```promql
sum by (outcome) (rate(worker_executions_processed_total{connector="binance-spot"}[5m]))
```

### Fill processing p95 latency
```promql
histogram_quantile(
  0.95,
  sum by (le) (rate(worker_executions_process_duration_seconds_bucket{connector="binance-spot"}[5m]))
)
```

### Order fill update throughput
```promql
sum by (status) (rate(worker_order_fill_updates_total[5m]))
```

### Balance update failures by asset
```promql
sum by (asset) (rate(worker_balance_updates_total{outcome="failed"}[5m]))
```

### Outbox append failures by event type
```promql
sum by (event_type) (rate(worker_outbox_append_total{outcome="failed"}[5m]))
```

## Alert Seeds

- `worker_executions_processed_total{outcome="failed"}` rate > 0 for 10 minutes.
- p95 `worker_executions_process_duration` above normal SLO for 15 minutes.
- Any sustained increase in `worker_outbox_append_total{outcome="failed"}`.
