param()

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$realmPath = Join-Path $repoRoot 'pilot/keycloak/realm/jitsi-dev-realm.json'
$composePath = Join-Path $repoRoot 'docker-compose.yml'

function Fail([string]$message) {
  throw $message
}

if (-not (Test-Path $realmPath)) {
  Fail "Keycloak realm file not found: $realmPath"
}

if (-not (Test-Path $composePath)) {
  Fail "Compose file not found: $composePath"
}

$realm = Get-Content -Raw -Path $realmPath | ConvertFrom-Json
$users = @($realm.users)

if ($users.Count -eq 0) {
  Fail 'Keycloak realm must define at least one seeded user.'
}

$usersMissingId = @($users | Where-Object { [string]::IsNullOrWhiteSpace($_.id) })
if ($usersMissingId.Count -gt 0) {
  $usernames = $usersMissingId | ForEach-Object { $_.username }
  Fail ("All seeded Keycloak users must define explicit stable ids. Missing for: {0}" -f ($usernames -join ', '))
}

$duplicateIds = $users |
  Group-Object -Property id |
  Where-Object { $_.Count -gt 1 }

if ($duplicateIds.Count -gt 0) {
  $duplicates = $duplicateIds | ForEach-Object { $_.Name }
  Fail ("Seeded Keycloak user ids must be unique. Duplicate ids: {0}" -f ($duplicates -join ', '))
}

$composeText = Get-Content -Raw -Path $composePath

if ($composeText -notmatch '(?m)^\s*-\s*pgdata:/var/lib/postgresql/data\s*$') {
  Fail 'docker-compose.yml must mount pgdata to /var/lib/postgresql/data to preserve Postgres application data across recreates.'
}

if ($composeText -match '(?m)^\s*-\s*pgdata:/var/lib/postgresql\s*$') {
  Fail 'docker-compose.yml still contains legacy pgdata mount to /var/lib/postgresql. Use /var/lib/postgresql/data instead.'
}

Write-Host 'validate-dev-stack-config: OK'
Write-Host ("validate-dev-stack-config: verified {0} seeded Keycloak user(s) with stable ids" -f $users.Count)
Write-Host 'validate-dev-stack-config: verified Postgres volume mount path'