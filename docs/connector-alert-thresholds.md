# Connector Alert Thresholds (Log + Metrics)

This note documents baseline alert thresholds for Binance connector catch-up and replay behavior.

## Profile

Selected default: balanced thresholds.

## Metrics Rules

- Warning: `worker.connector.errors.total{operation="catchup_run"}` increases by `>= 3` within 5 minutes.
- Warning: p95 `worker.connector.poll.duration{operation="catchup_run"}` is above 15 seconds for 10 minutes.
- Critical/page: connector health remains `DOWN` for 5 minutes or longer.
- Critical/page: `worker.connector.errors.total` increases by `>= 10` within 15 minutes.

## Log Rules

- Warning: count WARN logs containing `Connector catch-up failed` and alert when count reaches 3 in 5 minutes.
- Critical/page: alert on repeated bursts of the same connector error code (example: `HTTP_429`) over 10 minutes.
- Warning: alert on replay request failures using logs containing `Connector replay request failed`.

## Triage Notes

- Check admin health endpoint: `GET /v1/admin/connector/health`.
- Trigger a manual catch-up replay when needed: `POST /v1/admin/connector/catch-up/replay`.
- Review replay queue depth metric `worker.connector.replay.queue.depth` for backlogs.
