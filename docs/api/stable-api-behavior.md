# Stable API Behavior

## Error Contract

The API uses RFC7807 Problem Details for error responses:

- `Content-Type: application/problem+json`
- Stable fields: `type`, `title`, `status`, `detail`
- Machine code (when applicable): `code` in `properties`

## Idempotency Behavior (`/v1/orders/**`)

Header: `Idempotency-Key` is required on opted-in paths.

Outcomes:

- Missing key: `400` + `code=IDEMPOTENCY_KEY_REQUIRED`
- Same key + different payload hash: `409` + `code=IDEMPOTENCY_REQUEST_MISMATCH`
- Same key still processing: `409` + `code=IDEMPOTENCY_IN_PROGRESS`
- Same key previously failed: `409` + `code=IDEMPOTENCY_PREVIOUSLY_FAILED`
- Same key expired: `409` + `code=IDEMPOTENCY_KEY_EXPIRED`
- Same key completed and not expired: replay original status/body, `X-Idempotency-Status: replayed`

Response header `X-Idempotency-Status` is set to:

- `new`, `replayed`, `missing`, `mismatch`, `in_progress`, `previously_failed`, or `expired`

## Risk and Domain Errors

- Risk violations return `409` with `type=/problems/risk-violation` and `code`.
- Wallet/order validation errors return stable problem types in `GlobalExceptionHandler`.
