param(
    [string]$DockerContainer = 'rule-engine-mysql',
    [string]$Database = 'rule_engine',
    [string]$RestoreDatabase = 'rule_engine_restore_gate',
    [string]$OutputDirectory = 'target/quality-gates'
)

$ErrorActionPreference = 'Stop'

if (-not $env:MYSQL_ROOT_PASSWORD) {
    throw 'MYSQL_ROOT_PASSWORD must be provided through the environment; the script never reads or writes credentials from a file.'
}
foreach ($name in @($Database, $RestoreDatabase)) {
    if ($name -notmatch '^[A-Za-z0-9_]+$') {
        throw "Database name is unsafe: $name"
    }
}

$dumpPath = '/tmp/tianshu-rule-engine-restore-gate.sql'
$started = Get-Date
$restoreCreated = $false

function Invoke-MySql([string[]]$Arguments) {
    & docker exec -e "MYSQL_PWD=$env:MYSQL_ROOT_PASSWORD" $DockerContainer mysql -uroot @Arguments
    if ($LASTEXITCODE -ne 0) { throw "mysql command failed with exit code $LASTEXITCODE" }
}

function Invoke-ContainerShell([string]$Command) {
    & docker exec -e "MYSQL_PWD=$env:MYSQL_ROOT_PASSWORD" $DockerContainer sh -c $Command
    if ($LASTEXITCODE -ne 0) { throw "container command failed with exit code $LASTEXITCODE" }
}

try {
    Write-Host "Creating logical backup inside $DockerContainer..."
    Invoke-ContainerShell "mysqldump --single-transaction --routines --events --triggers $Database > $dumpPath"
    Invoke-MySql @('-e', "DROP DATABASE IF EXISTS $RestoreDatabase; CREATE DATABASE $RestoreDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
    $restoreCreated = $true
    Write-Host "Restoring backup into $RestoreDatabase..."
    Invoke-ContainerShell "mysql $RestoreDatabase < $dumpPath"

    $query = "SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema='$RestoreDatabase'; SELECT 'rule_definition' AS name, COUNT(*) AS row_count FROM $RestoreDatabase.rule_definition UNION ALL SELECT 'decision_artifact', COUNT(*) FROM $RestoreDatabase.decision_artifact UNION ALL SELECT 'rule_execution_log', COUNT(*) FROM $RestoreDatabase.rule_execution_log UNION ALL SELECT 'rule_billing_record', COUNT(*) FROM $RestoreDatabase.rule_billing_record UNION ALL SELECT 'rule_lifecycle_event', COUNT(*) FROM $RestoreDatabase.rule_lifecycle_event;"
    $raw = @(Invoke-MySql @('-N', '-B', '-e', $query))
    $tableCount = [int]$raw[0]
    if ($tableCount -lt 50) { throw "Restored table count is unexpectedly low: $tableCount" }
    $rows = @($raw | Select-Object -Skip 1 | ForEach-Object {
        $parts = $_ -split "`t", 2
        [ordered]@{ name = $parts[0]; rowCount = [int64]$parts[1] }
    })
    New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
    $summary = [ordered]@{
        result = 'PASS'
        generatedAt = (Get-Date).ToUniversalTime().ToString('o')
        sourceDatabase = $Database
        restoreDatabase = $RestoreDatabase
        tableCount = $tableCount
        keyTableCounts = $rows
        durationSeconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 2)
    }
    $summary | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $OutputDirectory 'backup-restore-summary.json') -Encoding utf8
    Write-Output ($summary | ConvertTo-Json -Depth 6)
}
finally {
    if ($restoreCreated) {
        try { Invoke-MySql @('-e', "DROP DATABASE IF EXISTS $RestoreDatabase;") } catch { Write-Warning $_ }
    }
    try { Invoke-ContainerShell "rm -f $dumpPath" } catch { Write-Warning $_ }
}
