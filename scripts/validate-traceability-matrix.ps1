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

function Assert-Match {
  param(
    [string]$Content,
    [string]$Pattern,
    [string]$Description
  )

  if ($Content -notmatch $Pattern) {
    throw "$Description does not match expected pattern: $Pattern"
  }
}

function Get-ReferencedTestFiles {
  param([string]$Content)

  $matches = [regex]::Matches($Content, '`(?<file>(?:[^`]+Test\.java|[^`]+\.test\.(?:ts|tsx)))`')
  $files = foreach ($match in $matches) {
    $candidate = $match.Groups['file'].Value.Trim()
    if ($candidate.Contains('*')) {
      continue
    }

    Split-Path -Leaf $candidate
  }

  $files | Sort-Object -Unique
}

function Get-TestFileBasenames {
  param(
    [string]$Root,
    [string[]]$Includes
  )

  if (-not (Test-Path -LiteralPath $Root)) {
    return @()
  }

  Get-ChildItem -LiteralPath $Root -Recurse -File -Include $Includes |
    Select-Object -ExpandProperty Name |
    Sort-Object -Unique
}

function Get-AuditGapSummaryCounts {
  param([string[]]$Lines)

  $counts = @{
    critical = 0
    medium = 0
    low = 0
  }

  foreach ($line in $Lines) {
    if ($line -notmatch '^\|\s+[^|]+\|\s+AC\d+\s+\|.*\|\s+gap\s+\|\s+(critical|medium|low)\s+\|\s*$') {
      continue
    }

    $counts[$Matches[1]]++
  }

  $counts
}

function Get-AuditSummaryValue {
  param(
    [string[]]$Lines,
    [string]$Label
  )

  $line = $Lines |
    Where-Object {
      $columns = $_.Split('|') | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
      $columns.Count -ge 2 -and $columns[0] -eq $Label
    } |
    Select-Object -First 1
  if (-not $line) {
    throw "Missing audit summary row for '$Label'."
  }

  $columns = $line.Split('|') | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
  if ($columns.Count -lt 2) {
    throw "Audit summary row has unexpected format: $line"
  }

  [int]($columns[1] -replace '\*', '')
}

function Get-FrStatus {
  param(
    [string[]]$Lines,
    [int]$FrId
  )

  $line = $Lines | Where-Object { $_ -match ("^\| FR{0} \|" -f $FrId) } | Select-Object -First 1
  if (-not $line) {
    throw "Missing FR$FrId row in requirements traceability matrix."
  }

  $columns = $line.Split('|') | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
  if ($columns.Count -lt 7) {
    throw "FR$FrId row does not have enough columns: $line"
  }

  $columns[4]
}

$traceabilityPath = Join-Path $RepositoryRoot 'docs/requirements-traceability.md'
$auditPath = Join-Path $RepositoryRoot '_bmad-output/implementation-artifacts/10-1-ac-coverage-gap-audit.md'
$prTemplatePath = Join-Path $RepositoryRoot '.github/PULL_REQUEST_TEMPLATE.md'

Write-Step 'Checking traceability artifacts exist'
Assert-PathExists -Path $traceabilityPath -Description 'Requirements traceability document'
Assert-PathExists -Path $auditPath -Description 'AC coverage audit'
Assert-PathExists -Path $prTemplatePath -Description 'PR template'

$traceability = Get-Content -LiteralPath $traceabilityPath -Raw
$audit = Get-Content -LiteralPath $auditPath -Raw
$prTemplate = Get-Content -LiteralPath $prTemplatePath -Raw
$auditLines = $audit -split "`r?`n"

Write-Step 'Checking canonical coverage vocabulary and hook references'
Assert-Contains -Content $traceability -Needle '## Coverage Status Vocabulary' -Description 'Traceability document'
Assert-Contains -Content $traceability -Needle 'AUTO' -Description 'Traceability coverage vocabulary'
Assert-Contains -Content $traceability -Needle 'MANUAL' -Description 'Traceability coverage vocabulary'
Assert-Contains -Content $traceability -Needle 'PARTIAL' -Description 'Traceability coverage vocabulary'
Assert-Contains -Content $traceability -Needle 'GAP' -Description 'Traceability coverage vocabulary'
Assert-Contains -Content $traceability -Needle '_bmad-output/implementation-artifacts/10-1-ac-coverage-gap-audit.md' -Description 'Traceability AC-level source'
Assert-Contains -Content $traceability -Needle '.github/PULL_REQUEST_TEMPLATE.md' -Description 'Traceability process hook reference'
Assert-Contains -Content $traceability -Needle 'scripts/validate-traceability-matrix.ps1' -Description 'Traceability validation reference'

Write-Step 'Checking FR rows and status discipline'
$frLines = $traceability -split "`r?`n" | Where-Object { $_ -match '^\| FR\d{1,2} \|' }
if ($frLines.Count -ne 32) {
  throw "Expected 32 FR rows with canonical statuses, found $($frLines.Count)."
}

$frIds = @()
foreach ($line in $frLines) {
  if ($line -notmatch '^\| FR(\d{1,2}) \|') {
    throw "FR row has unexpected format: $line"
  }

  $columns = $line.Split('|') | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
  if ($columns.Count -lt 7) {
    throw "FR row does not have enough columns: $line"
  }

  $status = $columns[4]
  if ($status -notin @('AUTO', 'MANUAL', 'PARTIAL', 'GAP')) {
    throw "FR row has invalid status '$status': $line"
  }

  $frIds += [int]$Matches[1]
}

$frIds = $frIds | Sort-Object
for ($id = 1; $id -le 32; $id++) {
  if ($frIds -notcontains $id) {
    throw "Missing FR$id row in requirements traceability matrix."
  }
}

if ($traceability -match '\bTODO\b') {
  throw 'Traceability document still contains TODO placeholders.'
}

if ((Get-FrStatus -Lines $frLines -FrId 12) -notin @('AUTO', 'MANUAL', 'PARTIAL')) {
  throw 'FR12 must not remain a GAP after traceability normalization.'
}
if ((Get-FrStatus -Lines $frLines -FrId 31) -notin @('AUTO', 'MANUAL', 'PARTIAL')) {
  throw 'FR31 must not remain a GAP after traceability normalization.'
}
if ((Get-FrStatus -Lines $frLines -FrId 32) -notin @('AUTO', 'MANUAL', 'PARTIAL')) {
  throw 'FR32 must not remain a GAP after traceability normalization.'
}

Write-Step 'Checking legacy frontend path drift is removed from traceability surfaces'
Assert-NotContains -Content $traceability -Needle 'frontend/src/' -Description 'Traceability document'
Assert-NotContains -Content $audit -Needle 'frontend/src/' -Description 'AC coverage audit'

Write-Step 'Checking referenced audit test files exist in the repository'
$backendTestFiles = Get-TestFileBasenames -Root (Join-Path $RepositoryRoot 'backend/src/test') -Includes @('*Test.java')
$frontendTestFiles = Get-TestFileBasenames -Root (Join-Path $RepositoryRoot 'frontend-qwik/src') -Includes @('*.test.ts', '*.test.tsx')
$referencedTestFiles = Get-ReferencedTestFiles -Content $audit
$missingReferencedFiles = New-Object System.Collections.Generic.List[string]

foreach ($referencedFile in $referencedTestFiles) {
  if ($referencedFile.EndsWith('Test.java')) {
    if ($backendTestFiles -notcontains $referencedFile) {
      $missingReferencedFiles.Add($referencedFile)
    }
    continue
  }

  if (($referencedFile.EndsWith('.test.ts') -or $referencedFile.EndsWith('.test.tsx')) -and $frontendTestFiles -notcontains $referencedFile) {
    $missingReferencedFiles.Add($referencedFile)
  }
}

if ($missingReferencedFiles.Count -gt 0) {
  $missingList = $missingReferencedFiles | Sort-Object -Unique
  throw ('AC coverage audit references test files that do not exist: ' + ($missingList -join ', '))
}

Write-Step 'Checking audit summary counts match gap rows'
$gapCounts = Get-AuditGapSummaryCounts -Lines $auditLines
$criticalSummary = Get-AuditSummaryValue -Lines $auditLines -Label 'critical'
$mediumSummary = Get-AuditSummaryValue -Lines $auditLines -Label 'medium'
$lowSummary = Get-AuditSummaryValue -Lines $auditLines -Label 'low'
$totalSummary = Get-AuditSummaryValue -Lines $auditLines -Label '**ИТОГО**'
$computedTotal = $gapCounts.critical + $gapCounts.medium + $gapCounts.low

if ($criticalSummary -ne $gapCounts.critical) {
  throw "AC coverage audit critical gap count mismatch: summary=$criticalSummary, computed=$($gapCounts.critical)"
}
if ($mediumSummary -ne $gapCounts.medium) {
  throw "AC coverage audit medium gap count mismatch: summary=$mediumSummary, computed=$($gapCounts.medium)"
}
if ($lowSummary -ne $gapCounts.low) {
  throw "AC coverage audit low gap count mismatch: summary=$lowSummary, computed=$($gapCounts.low)"
}
if ($totalSummary -ne $computedTotal) {
  throw "AC coverage audit total gap count mismatch: summary=$totalSummary, computed=$computedTotal"
}

Write-Step 'Checking PR template includes traceability checklist'
Assert-Contains -Content $prTemplate -Needle 'traceability' -Description 'PR template'
Assert-Contains -Content $prTemplate -Needle 'requirements-traceability.md' -Description 'PR template'
Assert-Contains -Content $prTemplate -Needle '48 hours' -Description 'PR template'

Write-Step 'Traceability artifacts validated successfully'