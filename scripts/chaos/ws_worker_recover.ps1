$ErrorActionPreference = "Stop"

$RootDir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$ComposeFile = Join-Path $RootDir "deploy\docker-compose.yml"
$EnvFile = if ($args.Count -gt 0) { $args[0] } else { Join-Path $RootDir "deploy\.env.example" }
$RecoveryTimeoutSeconds = if ($env:RECOVERY_TIMEOUT_SECONDS) { [int]$env:RECOVERY_TIMEOUT_SECONDS } else { 90 }

function Invoke-Compose {
  param([Parameter(Mandatory = $true)][string[]]$ComposeArgs)
  & docker compose --env-file $EnvFile -f $ComposeFile @ComposeArgs
}

function Get-WsState {
  $postgresUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "trading" }
  $postgresDb = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "trading" }
  $sql = "SELECT COALESCE(ws_connection_state, '') FROM connector_health_state WHERE connector_name='binance-spot';"
  $value = Invoke-Compose -ComposeArgs @("exec", "-T", "postgres", "psql", "-U", $postgresUser, "-d", $postgresDb, "-tAc", $sql)
  return ($value -join "").Trim()
}

function Get-LastWsConnectedAt {
  $postgresUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "trading" }
  $postgresDb = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "trading" }
  $sql = "SELECT COALESCE(to_char(last_ws_connected_at, 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"'), '') FROM connector_health_state WHERE connector_name='binance-spot';"
  $value = Invoke-Compose -ComposeArgs @("exec", "-T", "postgres", "psql", "-U", $postgresUser, "-d", $postgresDb, "-tAc", $sql)
  return ($value -join "").Trim()
}

function Wait-ForWsUp {
  param([Parameter(Mandatory = $true)][int]$TimeoutSeconds)
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $state = ""
    try {
      $state = Get-WsState
    } catch {
      $state = ""
    }
    if ($state -eq "UP") {
      return
    }
    Start-Sleep -Seconds 2
  }
  throw "Timed out waiting for ws_connection_state=UP"
}

$apiKey = if ($env:CONNECTOR_BINANCE_API_KEY) { $env:CONNECTOR_BINANCE_API_KEY } else { $env:BINANCE_API_KEY }
$apiSecret = if ($env:CONNECTOR_BINANCE_API_SECRET) { $env:CONNECTOR_BINANCE_API_SECRET } else { $env:BINANCE_API_SECRET }
$apiKeyFile = if ($env:CONNECTOR_BINANCE_API_KEY_FILE) { $env:CONNECTOR_BINANCE_API_KEY_FILE } else { $env:BINANCE_API_KEY_FILE }
$apiSecretFile = if ($env:CONNECTOR_BINANCE_API_SECRET_FILE) { $env:CONNECTOR_BINANCE_API_SECRET_FILE } else { $env:BINANCE_API_SECRET_FILE }

if ([string]::IsNullOrWhiteSpace($apiKey) -and [string]::IsNullOrWhiteSpace($apiKeyFile)) {
  throw "Binance API key is required. Set CONNECTOR_BINANCE_API_KEY or CONNECTOR_BINANCE_API_KEY_FILE."
}
if ([string]::IsNullOrWhiteSpace($apiSecret) -and [string]::IsNullOrWhiteSpace($apiSecretFile)) {
  throw "Binance API secret is required. Set CONNECTOR_BINANCE_API_SECRET or CONNECTOR_BINANCE_API_SECRET_FILE."
}

if (-not $env:WORKER_EXECUTION_ADAPTER) { $env:WORKER_EXECUTION_ADAPTER = "binance" }
if (-not $env:CONNECTOR_BINANCE_ENABLED) { $env:CONNECTOR_BINANCE_ENABLED = "true" }
if (-not $env:CONNECTOR_BINANCE_WS_ENABLED) { $env:CONNECTOR_BINANCE_WS_ENABLED = "true" }

Invoke-Compose -ComposeArgs @("up", "-d", "postgres", "kafka", "worker-exec")
Wait-ForWsUp -TimeoutSeconds $RecoveryTimeoutSeconds
$beforeConnectedAt = Get-LastWsConnectedAt

$killStart = Get-Date
Invoke-Compose -ComposeArgs @("kill", "worker-exec")
Invoke-Compose -ComposeArgs @("up", "-d", "worker-exec")
Wait-ForWsUp -TimeoutSeconds $RecoveryTimeoutSeconds
$afterConnectedAt = Get-LastWsConnectedAt

$elapsed = [int][Math]::Floor(((Get-Date) - $killStart).TotalSeconds)
if ([string]::IsNullOrWhiteSpace($afterConnectedAt)) {
  throw "Worker recovered but last_ws_connected_at is empty."
}
if (-not [string]::IsNullOrWhiteSpace($beforeConnectedAt) -and $beforeConnectedAt -eq $afterConnectedAt) {
  throw "Worker recovered but last_ws_connected_at did not change."
}
if ($elapsed -gt $RecoveryTimeoutSeconds) {
  throw "Worker recovered after timeout elapsed_seconds=$elapsed timeout_seconds=$RecoveryTimeoutSeconds"
}

Write-Output "RECOVERY_OK elapsed_seconds=$elapsed before_connected_at=$beforeConnectedAt after_connected_at=$afterConnectedAt"
