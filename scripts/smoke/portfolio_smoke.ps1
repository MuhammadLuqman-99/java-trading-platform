param(
  [string]$BaseUrl = "http://localhost:8081",
  [string]$AccountId = "11111111-1111-1111-1111-111111111111",
  [string]$Token = $env:TOKEN
)

if ([string]::IsNullOrWhiteSpace($Token)) {
  Write-Error "TOKEN is required (TRADER role). Set env:TOKEN or pass -Token."
  exit 1
}

$headers = @{
  Authorization = "Bearer $Token"
  Accept = "application/json"
}

Write-Host "GET $BaseUrl/v1/balances?accountId=$AccountId"
Invoke-RestMethod -Method GET -Uri "$BaseUrl/v1/balances?accountId=$AccountId" -Headers $headers |
  ConvertTo-Json -Depth 10

Write-Host "GET $BaseUrl/v1/portfolio?accountId=$AccountId"
Invoke-RestMethod -Method GET -Uri "$BaseUrl/v1/portfolio?accountId=$AccountId" -Headers $headers |
  ConvertTo-Json -Depth 10
