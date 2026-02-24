#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
ACCOUNT_ID="${ACCOUNT_ID:-11111111-1111-1111-1111-111111111111}"
TOKEN="${TOKEN:-}"

if [[ -z "${TOKEN}" ]]; then
  echo "TOKEN is required (TRADER role)."
  exit 1
fi

echo "GET ${BASE_URL}/v1/balances?accountId=${ACCOUNT_ID}"
curl -sS "${BASE_URL}/v1/balances?accountId=${ACCOUNT_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Accept: application/json" | tee /dev/stderr >/dev/null

echo
echo "GET ${BASE_URL}/v1/portfolio?accountId=${ACCOUNT_ID}"
curl -sS "${BASE_URL}/v1/portfolio?accountId=${ACCOUNT_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Accept: application/json" | tee /dev/stderr >/dev/null
