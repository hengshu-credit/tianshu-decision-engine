param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$RequestFile,
    [int]$Concurrency = 20,
    [int]$WarmupSeconds = 10,
    [int]$DurationSeconds = 60,
    [double]$MaxErrorRate = 0.01,
    [double]$MaxP95Ms = 500,
    [double]$MinThroughput = 10,
    [switch]$SkipBackup,
    [string]$BackupFile = 'target/quality-gates/staging-backup.sql'
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$summary = [ordered]@{
    baseUrl = $BaseUrl
    status = 'RUNNING'
    readiness = 'PENDING'
    capacity = 'PENDING'
    backup = 'SKIPPED'
    e2e = 'PENDING'
    startedAt = (Get-Date).ToUniversalTime().ToString('o')
}
Push-Location $repoRoot
try {
    node scripts/quality-gates/wait-for-readiness.mjs --base-url $BaseUrl --timeout-seconds 180 --interval-ms 1000
    if ($LASTEXITCODE -ne 0) { throw 'staging readiness gate failed' }
    $summary['readiness'] = 'PASS'

    node scripts/quality-gates/run-capacity-gate.mjs `
        --url "$BaseUrl/api/rule/open/execute/RISK_RULE" `
        --request-file $RequestFile `
        --concurrency $Concurrency `
        --warmup-seconds $WarmupSeconds `
        --duration-seconds $DurationSeconds `
        --max-error-rate $MaxErrorRate `
        --max-p95-ms $MaxP95Ms `
        --min-throughput $MinThroughput `
        --report-dir target/quality-gates
    if ($LASTEXITCODE -ne 0) { throw 'staging capacity gate failed' }
    $summary['capacity'] = 'PASS'

    if (-not $SkipBackup) {
        foreach ($name in @('MYSQL_HOST', 'MYSQL_PORT', 'MYSQL_BACKUP_USER', 'MYSQL_DATABASE')) {
            if (-not [Environment]::GetEnvironmentVariable($name)) {
                throw "missing backup environment variable: $name"
            }
        }
        $parent = Split-Path -Parent $BackupFile
        if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
        & mysqldump --single-transaction --routines --events --triggers `
            --host $env:MYSQL_HOST --port $env:MYSQL_PORT `
            --user $env:MYSQL_BACKUP_USER --password `
            $env:MYSQL_DATABASE | Set-Content -Path $BackupFile -Encoding UTF8
        if ($LASTEXITCODE -ne 0) { throw 'staging backup failed' }
        $summary['backup'] = 'PASS'
    }

    Push-Location rule-engine-builder-ui
    try {
        $env:E2E_BASE_URL = $BaseUrl
        npm run test:e2e:full
        if ($LASTEXITCODE -ne 0) { throw 'staging browser E2E failed' }
        $summary['e2e'] = 'PASS'
    } finally {
        Pop-Location
    }
    $summary['status'] = 'PASS'
    Write-Output 'staging gate passed'
} finally {
    $summary['finishedAt'] = (Get-Date).ToUniversalTime().ToString('o')
    if ($summary['status'] -eq 'RUNNING') { $summary['status'] = 'FAIL' }
    New-Item -ItemType Directory -Force -Path 'target/quality-gates' | Out-Null
    $summary | ConvertTo-Json | Set-Content -Path 'target/quality-gates/staging-gate-summary.json' -Encoding UTF8
    Pop-Location
}
