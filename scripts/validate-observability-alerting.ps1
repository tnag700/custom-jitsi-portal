param(
  [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

function Write-Step {
  param([string]$Message)
  Write-Host "==> $Message"
}

function Assert-PathExists {
  param(
    [string]$Path,
    [string]$Description
  )

  if (-not (Test-Path -LiteralPath $Path)) {
    throw "$Description is missing: $Path"
  }
}

function Assert-Contains {
  param(
    [string]$Content,
    [string]$Needle,
    [string]$Description
  )

  if (-not $Content.Contains($Needle)) {
    throw "$Description is missing required content: $Needle"
  }
}

function Assert-NotContains {
  param(
    [string]$Content,
    [string]$Needle,
    [string]$Description
  )

  if ($Content.Contains($Needle)) {
    throw "$Description contains forbidden content: $Needle"
  }
}

function Write-RenderedTemplate {
  param(
    [string]$TemplatePath,
    [string]$OutputPath,
    [hashtable]$Replacements
  )

  $rendered = Get-Content -LiteralPath $TemplatePath -Raw
  foreach ($replacement in $Replacements.GetEnumerator()) {
    $rendered = $rendered.Replace($replacement.Key, $replacement.Value)
  }
  Set-Content -LiteralPath $OutputPath -Value $rendered -Encoding UTF8
}

$prometheusDir = Join-Path $RepositoryRoot 'pilot/monitoring/prometheus'
$alertmanagerDir = Join-Path $RepositoryRoot 'pilot/monitoring/alertmanager'
$alertRulesPath = Join-Path $prometheusDir 'alert-rules.yml'
$prometheusConfigPath = Join-Path $prometheusDir 'prometheus.yml'
$alertmanagerTemplatePath = Join-Path $alertmanagerDir 'alertmanager.yml.template'
$composePath = Join-Path $RepositoryRoot 'docker-compose.monitoring.yml'

Write-Step 'Checking canonical alerting artifacts'
Assert-PathExists -Path $alertRulesPath -Description 'Prometheus alert rules'
Assert-PathExists -Path $prometheusConfigPath -Description 'Prometheus config'
Assert-PathExists -Path $alertmanagerTemplatePath -Description 'Alertmanager template'
Assert-PathExists -Path $composePath -Description 'Monitoring compose override'

$alertRules = Get-Content -LiteralPath $alertRulesPath -Raw
$prometheusConfig = Get-Content -LiteralPath $prometheusConfigPath -Raw
$alertmanagerTemplate = Get-Content -LiteralPath $alertmanagerTemplatePath -Raw
$composeConfig = Get-Content -LiteralPath $composePath -Raw

foreach ($alertName in @(
  'JitsiJoinSuccessRateLow',
  'JitsiJoinLatencyP95High',
  'JitsiJoinLatencyP99High',
  'JitsiAuthRefreshReuseSpike',
  'JitsiBackendUnavailable',
  'JitsiConfigCompatibilityBroken',
  'JitsiJoinReadinessBlocked',
  'JitsiJoinReadinessDegradedTooLong'
)) {
  Assert-Contains -Content $alertRules -Needle $alertName -Description 'Alert rules'
}

foreach ($annotation in @('dashboard:', 'runbook:', 'sli_window:')) {
  Assert-Contains -Content $alertRules -Needle $annotation -Description 'Alert annotations'
}

foreach ($forbiddenLabel in @('traceId', 'subjectId', 'meetingId', 'roomId', 'ip_address')) {
  Assert-NotContains -Content $alertRules -Needle $forbiddenLabel -Description 'Alert rules'
}

Assert-Contains -Content $prometheusConfig -Needle 'rule_files:' -Description 'Prometheus config'
Assert-Contains -Content $prometheusConfig -Needle 'alertmanagers:' -Description 'Prometheus config'
Assert-Contains -Content $alertmanagerTemplate -Needle '__ALERTMANAGER_WEBHOOK_URL__' -Description 'Alertmanager template'
Assert-Contains -Content $alertmanagerTemplate -Needle 'send_resolved: true' -Description 'Alertmanager template'
Assert-Contains -Content $composeConfig -Needle 'alertmanager:' -Description 'Monitoring compose override'
Assert-Contains -Content $composeConfig -Needle 'mock-alert-receiver:' -Description 'Monitoring compose override'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  throw 'Docker CLI is required to validate Prometheus and Alertmanager configuration.'
}

$prometheusMount = (Resolve-Path $prometheusDir).Path
$renderedPrometheusConfigPath = Join-Path $prometheusDir 'prometheus.rendered.yml'
$renderedAlertRulesPath = Join-Path $prometheusDir 'alert-rules.rendered.yml'
$renderedAlertmanagerPath = Join-Path $alertmanagerDir 'alertmanager.rendered.yml'
$alertmanagerMount = (Resolve-Path $alertmanagerDir).Path
$webhookUrl = if ($env:ALERTMANAGER_WEBHOOK_URL) {
  $env:ALERTMANAGER_WEBHOOK_URL
} else {
  'http://mock-alert-receiver:9080/alerts'
}
$monitoringEnvironment = if ($env:MONITORING_ENVIRONMENT) {
  $env:MONITORING_ENVIRONMENT
} else {
  'local'
}
$monitoringServiceName = if ($env:MONITORING_SERVICE_NAME) {
  $env:MONITORING_SERVICE_NAME
} else {
  'jitsi-backend'
}
$grafanaBaseUrl = if ($env:MONITORING_GRAFANA_BASE_URL) {
  $env:MONITORING_GRAFANA_BASE_URL
} else {
  'http://localhost:3001'
}

Write-Step 'Running promtool check rules'
try {
  Write-RenderedTemplate -TemplatePath $prometheusConfigPath -OutputPath $renderedPrometheusConfigPath -Replacements @{
    '__MONITORING_ENVIRONMENT__' = $monitoringEnvironment
    '__MONITORING_SERVICE_NAME__' = $monitoringServiceName
  }
  Write-RenderedTemplate -TemplatePath $alertRulesPath -OutputPath $renderedAlertRulesPath -Replacements @{
    '__MONITORING_GRAFANA_BASE_URL__' = $grafanaBaseUrl
  }
  & docker run --rm --entrypoint /bin/promtool -v "${prometheusMount}:/etc/prometheus:ro" prom/prometheus:v3.3.1 check rules /etc/prometheus/alert-rules.rendered.yml
  if ($LASTEXITCODE -ne 0) {
    throw 'promtool check rules failed.'
  }

  Write-Step 'Running amtool check-config'
  Write-RenderedTemplate -TemplatePath $alertmanagerTemplatePath -OutputPath $renderedAlertmanagerPath -Replacements @{
    '__ALERTMANAGER_WEBHOOK_URL__' = $webhookUrl
  }
  & docker run --rm --entrypoint /bin/amtool -v "${alertmanagerMount}:/etc/alertmanager:ro" prom/alertmanager:v0.28.1 check-config /etc/alertmanager/alertmanager.rendered.yml
  if ($LASTEXITCODE -ne 0) {
    throw 'amtool check-config failed.'
  }
} finally {
  Remove-Item -LiteralPath $renderedPrometheusConfigPath -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $renderedAlertRulesPath -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $renderedAlertmanagerPath -ErrorAction SilentlyContinue
}

Write-Step 'Alerting configuration validated successfully'