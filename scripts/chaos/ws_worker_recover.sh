#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.yml"
ENV_FILE="${1:-$ROOT_DIR/deploy/.env.example}"
RECOVERY_TIMEOUT_SECONDS="${RECOVERY_TIMEOUT_SECONDS:-90}"

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

query_ws_state() {
  compose exec -T postgres psql -U "${POSTGRES_USER:-trading}" -d "${POSTGRES_DB:-trading}" \
    -tAc "SELECT COALESCE(ws_connection_state, '') FROM connector_health_state WHERE connector_name='binance-spot';" \
    | tr -d '[:space:]'
}

query_last_ws_connected_at() {
  compose exec -T postgres psql -U "${POSTGRES_USER:-trading}" -d "${POSTGRES_DB:-trading}" \
    -tAc "SELECT COALESCE(to_char(last_ws_connected_at, 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"'), '') FROM connector_health_state WHERE connector_name='binance-spot';" \
    | tr -d '[:space:]'
}

wait_for_ws_up() {
  local timeout_seconds="$1"
  local start_epoch
  start_epoch="$(date +%s)"
  while true; do
    local state
    state="$(query_ws_state || true)"
    if [[ "$state" == "UP" ]]; then
      return 0
    fi
    local now
    now="$(date +%s)"
    if (( now - start_epoch >= timeout_seconds )); then
      echo "Timed out waiting for ws_connection_state=UP (last_state=$state)"
      return 1
    fi
    sleep 2
  done
}

if [[ -z "${CONNECTOR_BINANCE_API_KEY:-${BINANCE_API_KEY:-}}" && -z "${CONNECTOR_BINANCE_API_KEY_FILE:-${BINANCE_API_KEY_FILE:-}}" ]]; then
  echo "Binance API key is required. Set CONNECTOR_BINANCE_API_KEY or CONNECTOR_BINANCE_API_KEY_FILE."
  exit 2
fi

if [[ -z "${CONNECTOR_BINANCE_API_SECRET:-${BINANCE_API_SECRET:-}}" && -z "${CONNECTOR_BINANCE_API_SECRET_FILE:-${BINANCE_API_SECRET_FILE:-}}" ]]; then
  echo "Binance API secret is required. Set CONNECTOR_BINANCE_API_SECRET or CONNECTOR_BINANCE_API_SECRET_FILE."
  exit 2
fi

export WORKER_EXECUTION_ADAPTER="${WORKER_EXECUTION_ADAPTER:-binance}"
export CONNECTOR_BINANCE_ENABLED="${CONNECTOR_BINANCE_ENABLED:-true}"
export CONNECTOR_BINANCE_WS_ENABLED="${CONNECTOR_BINANCE_WS_ENABLED:-true}"

compose up -d postgres kafka worker-exec
wait_for_ws_up "$RECOVERY_TIMEOUT_SECONDS"
before_connected_at="$(query_last_ws_connected_at || true)"

kill_epoch="$(date +%s)"
compose kill worker-exec
compose up -d worker-exec

wait_for_ws_up "$RECOVERY_TIMEOUT_SECONDS"
after_connected_at="$(query_last_ws_connected_at || true)"
elapsed="$(( $(date +%s) - kill_epoch ))"

if [[ -z "$after_connected_at" ]]; then
  echo "Worker recovered but last_ws_connected_at is empty"
  exit 1
fi

if [[ -n "$before_connected_at" && "$before_connected_at" == "$after_connected_at" ]]; then
  echo "Worker recovered but last_ws_connected_at did not change"
  exit 1
fi

if (( elapsed > RECOVERY_TIMEOUT_SECONDS )); then
  echo "Worker recovered after timeout elapsed_seconds=$elapsed timeout_seconds=$RECOVERY_TIMEOUT_SECONDS"
  exit 1
fi

echo "RECOVERY_OK elapsed_seconds=$elapsed before_connected_at=$before_connected_at after_connected_at=$after_connected_at"
