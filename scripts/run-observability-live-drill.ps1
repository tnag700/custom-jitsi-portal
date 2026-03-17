param(
  [string]$FrontendUrl = "http://localhost:3000",
  [string]$BackendUrl = "http://localhost:8080",
  [string]$KeycloakBaseUrl = "http://localhost:8081",
  [string]$PrometheusUrl = "http://localhost:9090",
  [string]$GrafanaUrl = "http://localhost:3001",
  [string]$AlertmanagerUrl = "http://localhost:9093",
  [string]$AlertReceiverUrl = "http://localhost:9080",
  [string]$Username = "dev-admin",
  [string]$Password = "dev-admin-pass",
  [string]$TenantId = "tenant-1",
  [string]$ConfigSetId = "config-1",
  [ValidateSet('none', 'firing', 'resolved', 'cycle')]
  [string]$WaitForAlertState = "none",
  [string]$AlertName = "JitsiAuthRefreshReuseSpike",
  [int]$AlertWarmupSeconds = 20,
  [int]$AlertPollIntervalSeconds = 5,
  [int]$AlertWaitTimeoutSeconds = 180,
  [int]$QuietWindowSeconds = 135,
  [int]$PrometheusWaitSeconds = 20,
  [int]$Cycles = 1,
  [int]$CycleIntervalSeconds = 0,
  [int]$JoinSuccessRequestsPerCycle = 1,
  [int]$JoinFailureRequestsPerCycle = 1,
  [int]$RefreshPairsPerCycle = 1,
  [switch]$KeepArtifacts,
  [string]$SigningSecret = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($SigningSecret)) {
  $SigningSecret = if ($env:APP_MEETINGS_TOKEN_SIGNING_SECRET) {
    $env:APP_MEETINGS_TOKEN_SIGNING_SECRET
  } else {
    "01234567890123456789012345678901"
  }
}

function Write-Step {
  param([string]$Message)
  Write-Host "==> $Message"
}

function ConvertTo-Base64Url {
  param([byte[]]$Bytes)

  [Convert]::ToBase64String($Bytes).TrimEnd('=') -replace '\+', '-' -replace '/', '_'
}

function New-Hs256Jwt {
  param(
    [hashtable]$Payload,
    [string]$Secret
  )

  $headerJson = @{ typ = 'JWT'; alg = 'HS256' } | ConvertTo-Json -Compress
  $payloadJson = $Payload | ConvertTo-Json -Compress

  $encodedHeader = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($headerJson))
  $encodedPayload = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($payloadJson))
  $unsignedToken = "$encodedHeader.$encodedPayload"

  $hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($Secret))
  try {
    $signatureBytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($unsignedToken))
  } finally {
    $hmac.Dispose()
  }

  $signature = ConvertTo-Base64Url $signatureBytes
  "$unsignedToken.$signature"
}

function Merge-Hashtables {
  param(
    [hashtable]$Base,
    [hashtable]$Extra
  )

  $merged = @{}
  foreach ($entry in $Base.GetEnumerator()) {
    $merged[$entry.Key] = $entry.Value
  }
  foreach ($entry in $Extra.GetEnumerator()) {
    $merged[$entry.Key] = $entry.Value
  }

  $merged
}

function Invoke-CurlJsonRequest {
  param(
    [string]$Uri,
    [string]$Method = 'GET',
    [string]$CookieJar,
    [hashtable]$Headers = @{},
    [object]$Body
  )

  $curlArgs = @(
    '-sS',
    '-c', $CookieJar,
    '-b', $CookieJar,
    '-X', $Method,
    '-w', '__STATUS__:%{http_code}',
    $Uri
  )

  foreach ($header in $Headers.GetEnumerator()) {
    $curlArgs += '-H'
    $curlArgs += ("{0}: {1}" -f $header.Key, $header.Value)
  }

  if ($PSBoundParameters.ContainsKey('Body')) {
    $curlArgs += '-H'
    $curlArgs += 'Content-Type: application/json'
    $curlArgs += '--data-binary'
    $curlArgs += ($Body | ConvertTo-Json -Depth 10 -Compress)
  }

  $output = & curl.exe @curlArgs
  if ($LASTEXITCODE -ne 0) {
    throw "curl request failed for $Uri"
  }

  $marker = '__STATUS__:'
  $markerIndex = $output.LastIndexOf($marker)
  if ($markerIndex -lt 0) {
    throw "Не удалось разобрать статус curl для $Uri"
  }

  $rawBody = $output.Substring(0, $markerIndex)
  $statusCode = [int]$output.Substring($markerIndex + $marker.Length)
  $parsed = $null
  if (-not [string]::IsNullOrWhiteSpace($rawBody)) {
    try {
      $parsed = $rawBody | ConvertFrom-Json -Depth 20
    } catch {
      $parsed = $rawBody
    }
  }

  [pscustomobject]@{
    StatusCode = $statusCode
    Body = $parsed
    RawBody = $rawBody
  }
}

function Get-KeycloakLoginAction {
  param(
    [string]$Html,
    [string]$BaseUrl
  )

  $match = [regex]::Match($Html, 'id="kc-form-login"[\s\S]*?action="([^"]+)"')
  if (-not $match.Success) {
    throw "Не удалось найти action формы логина Keycloak."
  }

  $action = [System.Net.WebUtility]::HtmlDecode($match.Groups[1].Value)
  if ($action.StartsWith('/')) {
    return "$($BaseUrl.TrimEnd('/'))$action"
  }

  $action
}

function New-TraceId {
  param([string]$Prefix)
  "$Prefix-$([guid]::NewGuid().ToString('N'))"
}

function New-IdempotencyKey {
  param([string]$Prefix)
  "$Prefix-$([guid]::NewGuid().ToString('N'))"
}

function Get-PrometheusMetric {
  param([string]$Query)

  $encodedQuery = [System.Uri]::EscapeDataString($Query)
  Invoke-RestMethod -Uri "$PrometheusUrl/api/v1/query?query=$encodedQuery"
}

function Wait-ForPrometheusApi {
  param(
    [int]$TimeoutSeconds = 90,
    [int]$PollIntervalSeconds = 5
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    try {
      Invoke-RestMethod -Uri "$PrometheusUrl/-/ready" | Out-Null
      return
    } catch {
      Start-Sleep -Seconds $PollIntervalSeconds
    }
  } while ((Get-Date) -lt $deadline)

  throw "Prometheus API не стало ready за $TimeoutSeconds секунд."
}

function Get-PrometheusValue {
  param($MetricResponse)

  if ($null -eq $MetricResponse -or $null -eq $MetricResponse.data -or $null -eq $MetricResponse.data.result -or $MetricResponse.data.result.Count -eq 0) {
    return '0'
  }

  $MetricResponse.data.result[0].value[1]
}

function Invoke-AlertReceiverRequest {
  param(
    [string]$Path,
    [string]$Method = 'GET'
  )

  Invoke-RestMethod -Uri "$AlertReceiverUrl$Path" -Method $Method
}

function Get-AlertmanagerAlerts {
  Invoke-RestMethod -Uri "$AlertmanagerUrl/api/v2/alerts"
}

function Find-AlertNotification {
  param(
    [object[]]$Notifications,
    [string]$ExpectedAlertName,
    [string]$ExpectedStatus
  )

  foreach ($notification in @($Notifications)) {
    if ($null -eq $notification) {
      continue
    }

    if ($notification.status -eq $ExpectedStatus -and @($notification.alertNames) -contains $ExpectedAlertName) {
      return $notification
    }
  }

  $null
}

function Wait-ForAlertNotification {
  param(
    [string]$ExpectedAlertName,
    [string]$ExpectedStatus,
    [int]$TimeoutSeconds,
    [int]$PollIntervalSeconds
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    $notificationsResponse = Invoke-AlertReceiverRequest -Path '/notifications'
    $notification = Find-AlertNotification -Notifications @($notificationsResponse.notifications) -ExpectedAlertName $ExpectedAlertName -ExpectedStatus $ExpectedStatus
    if ($null -ne $notification) {
      return $notification
    }

    Start-Sleep -Seconds $PollIntervalSeconds
  } while ((Get-Date) -lt $deadline)

  throw "Не удалось дождаться $ExpectedStatus notification для alert $ExpectedAlertName через $TimeoutSeconds секунд."
}

function Wait-ForAlertmanagerState {
  param(
    [string]$ExpectedAlertName,
    [int]$TimeoutSeconds,
    [int]$PollIntervalSeconds
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    $alerts = @(Get-AlertmanagerAlerts)
    $matchingAlert = $alerts | Where-Object { $_.labels.alertname -eq $ExpectedAlertName -and $_.status.state -eq 'active' } | Select-Object -First 1
    if ($null -ne $matchingAlert) {
      return $matchingAlert
    }

    Start-Sleep -Seconds $PollIntervalSeconds
  } while ((Get-Date) -lt $deadline)

  throw "Не удалось дождаться firing state в Alertmanager для alert $ExpectedAlertName через $TimeoutSeconds секунд."
}

function Wait-ForAlertmanagerClear {
  param(
    [string]$ExpectedAlertName,
    [int]$TimeoutSeconds,
    [int]$PollIntervalSeconds
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    $alerts = @(Get-AlertmanagerAlerts)
    $matchingAlert = $alerts | Where-Object { $_.labels.alertname -eq $ExpectedAlertName -and $_.status.state -eq 'active' } | Select-Object -First 1
    if ($null -eq $matchingAlert) {
      return $true
    }

    Start-Sleep -Seconds $PollIntervalSeconds
  } while ((Get-Date) -lt $deadline)

  throw "Alertmanager не снял firing state для alert $ExpectedAlertName за $TimeoutSeconds секунд."
}

function Invoke-JoinRequest {
  param(
    [string]$MeetingId,
    [int]$ExpectedStatusCode,
    [string]$TracePrefix,
    [hashtable]$Headers,
    [string]$CookieJar
  )

  $response = Invoke-CurlJsonRequest -Uri "$BackendUrl/api/v1/meetings/$MeetingId/access-token" -Method 'POST' -CookieJar $CookieJar -Headers (
    Merge-Hashtables $Headers @{
      'X-Trace-Id' = (New-TraceId $TracePrefix)
    }
  )

  if ($response.StatusCode -ne $ExpectedStatusCode) {
    throw "Join request for $MeetingId returned unexpected status $($response.StatusCode): $($response.RawBody)"
  }

  $response
}

function Invoke-RefreshPair {
  param(
    [string]$MeetingId,
    [string]$UserId,
    [hashtable]$Headers,
    [string]$CookieJar,
    [string]$SigningSecret,
    [string]$FrontendUrl
  )

  $now = [DateTimeOffset]::UtcNow
  $refreshToken = New-Hs256Jwt -Payload @{
    iss = $FrontendUrl
    aud = 'jitsi-meet'
    sub = $UserId
    iat = $now.ToUnixTimeSeconds()
    exp = $now.AddHours(2).ToUnixTimeSeconds()
    jti = [guid]::NewGuid().ToString()
    tokenType = 'refresh'
    meetingId = $MeetingId
  } -Secret $SigningSecret

  $refreshSuccessResponse = Invoke-CurlJsonRequest -Uri "$BackendUrl/api/v1/auth/refresh" -Method 'POST' -CookieJar $CookieJar -Headers (
    Merge-Hashtables $Headers @{
      'X-Trace-Id' = (New-TraceId 'trace-refresh-success')
    }
  ) -Body @{
    refreshToken = $refreshToken
  }

  if ($refreshSuccessResponse.StatusCode -ne 200) {
    throw "Refresh success path завершился со статусом $($refreshSuccessResponse.StatusCode): $($refreshSuccessResponse.RawBody)"
  }

  $refreshReuseResponse = Invoke-CurlJsonRequest -Uri "$BackendUrl/api/v1/auth/refresh" -Method 'POST' -CookieJar $CookieJar -Headers (
    Merge-Hashtables $Headers @{
      'X-Trace-Id' = (New-TraceId 'trace-refresh-reuse')
    }
  ) -Body @{
    refreshToken = $refreshToken
  }

  if ($refreshReuseResponse.StatusCode -ne 409) {
    throw "Refresh reuse path вернул неожиданный статус $($refreshReuseResponse.StatusCode): $($refreshReuseResponse.RawBody)"
  }

  [pscustomobject]@{
    Success = $refreshSuccessResponse
    Reuse = $refreshReuseResponse
  }
}

function Remove-ObservabilityArtifacts {
  param(
    [string]$MeetingId,
    [string]$RoomId,
    [hashtable]$Headers,
    [string]$CookieJar
  )

  $cleanup = [ordered]@{
    attempted = $false
    meetingCanceled = $false
    roomDeleted = $false
  }

  if ([string]::IsNullOrWhiteSpace($MeetingId) -and [string]::IsNullOrWhiteSpace($RoomId)) {
    return [pscustomobject]$cleanup
  }

  $cleanup.attempted = $true

  if (-not [string]::IsNullOrWhiteSpace($MeetingId)) {
    $cancelResponse = Invoke-CurlJsonRequest -Uri "$BackendUrl/api/v1/meetings/$MeetingId/cancel" -Method 'POST' -CookieJar $CookieJar -Headers (
      Merge-Hashtables $Headers @{
        'X-Trace-Id' = (New-TraceId 'trace-cleanup-meeting')
      }
    )

    if ($cancelResponse.StatusCode -in @(200, 404, 409)) {
      $cleanup.meetingCanceled = $cancelResponse.StatusCode -eq 200 -or $cancelResponse.StatusCode -eq 409
    } else {
      throw "Cleanup cancel for meeting $MeetingId returned unexpected status $($cancelResponse.StatusCode): $($cancelResponse.RawBody)"
    }
  }

  if (-not [string]::IsNullOrWhiteSpace($RoomId)) {
    $deleteResponse = Invoke-CurlJsonRequest -Uri "$BackendUrl/api/v1/rooms/$RoomId" -Method 'DELETE' -CookieJar $CookieJar -Headers (
      Merge-Hashtables $Headers @{
        'X-Trace-Id' = (New-TraceId 'trace-cleanup-room')
      }
    )

    if ($deleteResponse.StatusCode -in @(204, 404)) {
      $cleanup.roomDeleted = $deleteResponse.StatusCode -eq 204
    } else {
      throw "Cleanup delete for room $RoomId returned unexpected status $($deleteResponse.StatusCode): $($deleteResponse.RawBody)"
    }
  }

  [pscustomobject]$cleanup
}

Write-Step "Logging in through Keycloak"
$cookieJar = Join-Path $env:TEMP ("jitsi-observability-drill-" + [guid]::NewGuid().ToString('N') + '.txt')
$baseHeaders = @{}
$createdRoomId = $null
$createdMeetingId = $null
$cleanupResult = [pscustomobject]@{
  attempted = $false
  meetingCanceled = $false
  roomDeleted = $false
}
$cleanupCompleted = $false
try {
  if ($WaitForAlertState -ne 'none') {
    Write-Step "Resetting mock alert receiver before smoke verification"
    Invoke-AlertReceiverRequest -Path '/notifications' -Method 'DELETE' | Out-Null
  }

  $loginPage = & curl.exe -sS -L -c $cookieJar -b $cookieJar "$BackendUrl/api/v1/auth/login"
  if ($LASTEXITCODE -ne 0) {
    throw "Не удалось открыть backend login redirect chain."
  }

  $loginAction = Get-KeycloakLoginAction -Html $loginPage -BaseUrl $KeycloakBaseUrl

  $loginFormBody = "username=$([System.Uri]::EscapeDataString($Username))&password=$([System.Uri]::EscapeDataString($Password))&credentialId=&login=Sign+In"
  $loginResult = & curl.exe -sS -L -c $cookieJar -b $cookieJar -o NUL -w "%{http_code}|%{url_effective}" -H "Content-Type: application/x-www-form-urlencoded" --data $loginFormBody $loginAction
  if ($LASTEXITCODE -ne 0) {
    throw "curl login POST завершился с ошибкой."
  }

  $loginStatus, $effectiveUrl = $loginResult -split '\|', 2
  if ([int]$loginStatus -ne 200 -or -not $effectiveUrl.StartsWith($FrontendUrl)) {
    throw "Логин через Keycloak завершился неожиданно: status=$loginStatus url=$effectiveUrl"
  }

  Write-Step "Loading authenticated profile"
  $me = (Invoke-CurlJsonRequest -Uri "$BackendUrl/api/v1/auth/me" -CookieJar $cookieJar).Body
  $csrf = (Invoke-CurlJsonRequest -Uri "$BackendUrl/api/v1/auth/csrf" -CookieJar $cookieJar).Body

  $baseHeaders[$csrf.headerName] = $csrf.token

  Write-Step "Creating room, meeting, and participant assignment"
  $roomResponse = Invoke-CurlJsonRequest -Uri "$BackendUrl/api/v1/rooms" -Method 'POST' -CookieJar $cookieJar -Headers (
    Merge-Hashtables $baseHeaders @{
      'X-Trace-Id' = (New-TraceId 'trace-room')
      'Idempotency-Key' = (New-IdempotencyKey 'room')
    }
  ) -Body @{
    name = "Observability Live Room $(Get-Date -Format 'yyyyMMdd-HHmmss')"
    description = 'Live metrics drill'
    tenantId = $TenantId
    configSetId = $ConfigSetId
  }

  if ($roomResponse.StatusCode -ne 201) {
    throw "Создание room завершилось со статусом $($roomResponse.StatusCode): $($roomResponse.RawBody)"
  }

  $createdRoomId = $roomResponse.Body.roomId

  $startsAt = (Get-Date).ToUniversalTime().AddMinutes(5).ToString('o')
  $endsAt = (Get-Date).ToUniversalTime().AddMinutes(65).ToString('o')

  $meetingResponse = Invoke-CurlJsonRequest -Uri "$BackendUrl/api/v1/rooms/$($roomResponse.Body.roomId)/meetings" -Method 'POST' -CookieJar $cookieJar -Headers (
    Merge-Hashtables $baseHeaders @{
      'X-Trace-Id' = (New-TraceId 'trace-meeting')
      'Idempotency-Key' = (New-IdempotencyKey 'meeting')
    }
  ) -Body @{
    title = 'Observability Drill'
    description = 'Synthetic flow for metrics'
    meetingType = 'instant'
    startsAt = $startsAt
    endsAt = $endsAt
    allowGuests = $true
    recordingEnabled = $false
  }

  if ($meetingResponse.StatusCode -ne 201) {
    throw "Создание meeting завершилось со статусом $($meetingResponse.StatusCode): $($meetingResponse.RawBody)"
  }

  $createdMeetingId = $meetingResponse.Body.meetingId

  $assignmentResponse = Invoke-CurlJsonRequest -Uri "$BackendUrl/api/v1/meetings/$($meetingResponse.Body.meetingId)/participants" -Method 'POST' -CookieJar $cookieJar -Headers (
    Merge-Hashtables $baseHeaders @{
      'X-Trace-Id' = (New-TraceId 'trace-assignment')
    }
  ) -Body @{
    subjectId = $me.id
    role = 'host'
  }

  if ($assignmentResponse.StatusCode -ne 201) {
    throw "Назначение participant завершилось со статусом $($assignmentResponse.StatusCode): $($assignmentResponse.RawBody)"
  }

  Write-Step "Generating traffic over time"
  $joinSuccessCount = 0
  $joinFailureCount = 0
  $refreshSuccessCount = 0
  $refreshReuseCount = 0
  $lastJoinSuccessStatus = $null
  $lastJoinFailureStatus = $null
  $lastRefreshSuccessStatus = $null
  $lastRefreshReuseStatus = $null

  for ($cycle = 1; $cycle -le $Cycles; $cycle++) {
    Write-Step "Traffic cycle $cycle/$Cycles"

    for ($requestIndex = 1; $requestIndex -le $JoinSuccessRequestsPerCycle; $requestIndex++) {
      $joinSuccessResponse = Invoke-JoinRequest -MeetingId $meetingResponse.Body.meetingId -ExpectedStatusCode 200 -TracePrefix 'trace-join-success' -Headers $baseHeaders -CookieJar $cookieJar
      $joinSuccessCount++
      $lastJoinSuccessStatus = $joinSuccessResponse.StatusCode
    }

    for ($requestIndex = 1; $requestIndex -le $JoinFailureRequestsPerCycle; $requestIndex++) {
      $joinFailureResponse = Invoke-JoinRequest -MeetingId 'meeting-does-not-exist' -ExpectedStatusCode 404 -TracePrefix 'trace-join-fail' -Headers $baseHeaders -CookieJar $cookieJar
      $joinFailureCount++
      $lastJoinFailureStatus = $joinFailureResponse.StatusCode
    }

    for ($requestIndex = 1; $requestIndex -le $RefreshPairsPerCycle; $requestIndex++) {
      $refreshResult = Invoke-RefreshPair -MeetingId $meetingResponse.Body.meetingId -UserId $me.id -Headers $baseHeaders -CookieJar $cookieJar -SigningSecret $SigningSecret -FrontendUrl $FrontendUrl
      $refreshSuccessCount++
      $refreshReuseCount++
      $lastRefreshSuccessStatus = $refreshResult.Success.StatusCode
      $lastRefreshReuseStatus = $refreshResult.Reuse.StatusCode
    }

    if ($cycle -lt $Cycles -and $CycleIntervalSeconds -gt 0) {
      Start-Sleep -Seconds $CycleIntervalSeconds
    }
  }

  Write-Step "Waiting for Prometheus scrape"
  Start-Sleep -Seconds $PrometheusWaitSeconds
  Wait-ForPrometheusApi

  Write-Step "Collecting Prometheus metrics"
  $joinAttempts = Get-PrometheusMetric 'jitsi_join_attempts_total'
  $joinSuccess = Get-PrometheusMetric 'jitsi_join_success_total'
  $joinFailure = Get-PrometheusMetric 'jitsi_join_failure_total{error_code="MEETING_NOT_FOUND"}'
  $joinLatencySuccess = Get-PrometheusMetric 'jitsi_join_latency_seconds_count{result="success"}'
  $joinLatencyFail = Get-PrometheusMetric 'jitsi_join_latency_seconds_count{result="fail"}'
  $refreshReuse = Get-PrometheusMetric 'jitsi_auth_refresh_events_total{event_type="refresh_reuse"}'
  $refreshIssued = Get-PrometheusMetric 'jitsi_auth_refresh_events_total{event_type="token_issued"}'
  $refreshTokenRefreshed = Get-PrometheusMetric 'jitsi_auth_refresh_events_total{event_type="token_refreshed"}'

  $alertState = $null
  $firingNotification = $null
  $resolvedNotification = $null

  if ($WaitForAlertState -ne 'none') {
    if ($AlertWarmupSeconds -gt 0) {
      Write-Step "Waiting for Prometheus rule evaluation warm-up"
      Start-Sleep -Seconds $AlertWarmupSeconds
    }

    Write-Step "Waiting for Alertmanager firing state for $AlertName"
    $alertState = Wait-ForAlertmanagerState -ExpectedAlertName $AlertName -TimeoutSeconds $AlertWaitTimeoutSeconds -PollIntervalSeconds $AlertPollIntervalSeconds

    Write-Step "Waiting for firing notification delivery for $AlertName"
    $firingNotification = Wait-ForAlertNotification -ExpectedAlertName $AlertName -ExpectedStatus 'firing' -TimeoutSeconds $AlertWaitTimeoutSeconds -PollIntervalSeconds $AlertPollIntervalSeconds
  }

  if ($WaitForAlertState -eq 'resolved' -or $WaitForAlertState -eq 'cycle') {
    Write-Step "Waiting quiet window for resolved notification"
    Start-Sleep -Seconds $QuietWindowSeconds

    Write-Step "Waiting for Alertmanager to clear firing state for $AlertName"
    Wait-ForAlertmanagerClear -ExpectedAlertName $AlertName -TimeoutSeconds $AlertWaitTimeoutSeconds -PollIntervalSeconds $AlertPollIntervalSeconds | Out-Null

    Write-Step "Waiting for resolved notification delivery for $AlertName"
    $resolvedNotification = Wait-ForAlertNotification -ExpectedAlertName $AlertName -ExpectedStatus 'resolved' -TimeoutSeconds $AlertWaitTimeoutSeconds -PollIntervalSeconds $AlertPollIntervalSeconds
  }

  if (-not $KeepArtifacts) {
    Write-Step "Cleaning up synthetic room and meeting"
    $cleanupResult = Remove-ObservabilityArtifacts -MeetingId $createdMeetingId -RoomId $createdRoomId -Headers $baseHeaders -CookieJar $cookieJar
    $cleanupCompleted = $true
  }

  [pscustomobject]@{
    user = $me.displayName
    userId = $me.id
    tenant = $me.tenant
    roomId = $roomResponse.Body.roomId
    meetingId = $meetingResponse.Body.meetingId
    trafficPlan = [ordered]@{
      cycles = $Cycles
      cycleIntervalSeconds = $CycleIntervalSeconds
      joinSuccessRequestsPerCycle = $JoinSuccessRequestsPerCycle
      joinFailureRequestsPerCycle = $JoinFailureRequestsPerCycle
      refreshPairsPerCycle = $RefreshPairsPerCycle
      prometheusWaitSeconds = $PrometheusWaitSeconds
    }
    requestSummary = [ordered]@{
      joinSuccessCompleted = $joinSuccessCount
      joinFailureCompleted = $joinFailureCount
      refreshSuccessCompleted = $refreshSuccessCount
      refreshReuseCompleted = $refreshReuseCount
      totalRequestsCompleted = ($joinSuccessCount + $joinFailureCount + $refreshSuccessCount + $refreshReuseCount)
    }
    lastStatuses = [ordered]@{
      joinSuccessStatus = $lastJoinSuccessStatus
      joinFailureStatus = $lastJoinFailureStatus
      refreshSuccessStatus = $lastRefreshSuccessStatus
      refreshReuseStatus = $lastRefreshReuseStatus
    }
    metrics = [ordered]@{
      join_attempts_total = (Get-PrometheusValue $joinAttempts)
      join_success_total = (Get-PrometheusValue $joinSuccess)
      join_failure_meeting_not_found = (Get-PrometheusValue $joinFailure)
      join_latency_success_count = (Get-PrometheusValue $joinLatencySuccess)
      join_latency_fail_count = (Get-PrometheusValue $joinLatencyFail)
      auth_refresh_reuse = (Get-PrometheusValue $refreshReuse)
      auth_refresh_token_issued = (Get-PrometheusValue $refreshIssued)
      auth_refresh_token_refreshed = (Get-PrometheusValue $refreshTokenRefreshed)
    }
    dashboards = [ordered]@{
      join_and_errors = "$GrafanaUrl/d/jitsi-join-errors/join-and-errors"
      service_health = "$GrafanaUrl/d/jitsi-service-health/service-health"
    }
    alerts = [ordered]@{
      alertmanagerUrl = $AlertmanagerUrl
      receiverUrl = $AlertReceiverUrl
      waitForAlertState = $WaitForAlertState
      alertName = $AlertName
      firingObserved = ($null -ne $firingNotification)
      resolvedObserved = ($null -ne $resolvedNotification)
      lastAlertmanagerState = if ($null -ne $alertState) { $alertState.status.state } else { $null }
      firingNotificationReceivedAt = if ($null -ne $firingNotification) { $firingNotification.receivedAt } else { $null }
      resolvedNotificationReceivedAt = if ($null -ne $resolvedNotification) { $resolvedNotification.receivedAt } else { $null }
    }
    cleanup = [ordered]@{
      keepArtifacts = [bool]$KeepArtifacts
      attempted = $cleanupResult.attempted
      meetingCanceled = $cleanupResult.meetingCanceled
      roomDeleted = $cleanupResult.roomDeleted
    }
  } | ConvertTo-Json -Depth 6
} finally {
  if (-not $KeepArtifacts -and -not $cleanupCompleted) {
    try {
      $cleanupResult = Remove-ObservabilityArtifacts -MeetingId $createdMeetingId -RoomId $createdRoomId -Headers $baseHeaders -CookieJar $cookieJar
    } catch {
      Write-Warning "Cleanup of observability artifacts failed: $($_.Exception.Message)"
    }
  }
  Remove-Item $cookieJar -ErrorAction SilentlyContinue
}