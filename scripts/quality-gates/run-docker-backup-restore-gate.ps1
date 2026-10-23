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

if ($Database -eq $RestoreDatabase) { throw 'Source and restore databases must differ' }
$dumpPath = '/tmp/tianshu-rule-engine-restore-' + [guid]::NewGuid().ToString('N') + '.sql'
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

$existing = @(Invoke-MySql @('-N', '-B', '-e', "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$RestoreDatabase';"))
if ([int]$existing[0] -ne 0) { throw 'Restore database already exists; refusing to overwrite it' }

function Read-TableFacts([string]$Schema, [string[]]$Tables) {
    $facts = [ordered]@{}
    foreach ($table in $Tables) {
        if ($table -notmatch '^[A-Za-z0-9_]+$') { throw 'Unexpected table identifier' }
        $qualified = "``$Schema``.``$table``"
        $raw = @(Invoke-MySql @('-N', '-B', '-e', "SELECT COUNT(*) FROM $qualified; CHECKSUM TABLE $qualified;"))
        $checksum = ($raw[1] -split "`t")[-1]
        if ($checksum -eq 'NULL' -or -not $checksum) { throw "Checksum unavailable: $table" }
        $facts[$table] = [ordered]@{ rowCount = [int64]$raw[0]; checksum = $checksum }
    }
    return $facts
}

try {
    $tables = @(Invoke-MySql @('-N', '-B', '-e', "SELECT table_name FROM information_schema.tables WHERE table_schema='$Database' AND table_type='BASE TABLE' ORDER BY table_name;"))
    if (-not $tables.Count) { throw 'Source database has no base tables' }
    $before = Read-TableFacts $Database $tables
    Write-Host "Creating logical backup inside $DockerContainer..."
    Invoke-ContainerShell "mysqldump --single-transaction --routines --events --triggers --hex-blob $Database > $dumpPath"
    Invoke-MySql @('-e', "CREATE DATABASE $RestoreDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
    $restoreCreated = $true
    Write-Host "Restoring backup into $RestoreDatabase..."
    Invoke-ContainerShell "mysql $RestoreDatabase < $dumpPath"

    $after = Read-TableFacts $RestoreDatabase $tables
    $mismatches = @($tables | Where-Object { ($before[$_] | ConvertTo-Json -Compress) -ne ($after[$_] | ConvertTo-Json -Compress) })
    New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
    $summary = [ordered]@{
        result = $(if ($mismatches.Count) { 'FAIL' } else { 'PASS' })
        generatedAt = (Get-Date).ToUniversalTime().ToString('o')
        sourceDatabase = $Database
        restoreDatabase = $RestoreDatabase
        tableCount = $tables.Count
        mismatches = $mismatches
        before = $before
        after = $after
        durationSeconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 2)
    }
    $summary | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $OutputDirectory 'backup-restore-summary.json') -Encoding utf8
    Write-Output ($summary | ConvertTo-Json -Depth 6)
    if ($mismatches.Count) { throw ('Restored table content mismatch: ' + ($mismatches -join ', ')) }
}
finally {
    if ($restoreCreated) {
        try { Invoke-MySql @('-e', "DROP DATABASE IF EXISTS $RestoreDatabase;") } catch { Write-Warning $_ }
    }
    try { Invoke-ContainerShell "rm -f $dumpPath" } catch { Write-Warning $_ }
}
