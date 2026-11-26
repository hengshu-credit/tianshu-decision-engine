param(
    [string]$DockerContainer = 'rule-engine-mysql',
    [string]$Database = 'rule_engine',
    [string]$RestoreDatabase = 'rule_engine_restore_gate',
    [string]$OutputDirectory = 'target/quality-gates',
    [switch]$DatabaseOnly,
    [switch]$KeepVerifiedDatabase,
    [string]$SftpBackupArchive,
    [string]$RestoredSftpHost,
    [ValidateRange(1,65535)][int]$RestoredSftpPort = 2222,
    [string]$SftpUsername = 'engine-storage',
    [string]$SftpHostFingerprint = $env:ENGINE_SFTP_HOST_FINGERPRINT
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
$dumpStarted = $false
$summary = $null

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

$scope = if ($DatabaseOnly) { 'DATABASE_ONLY' } else { 'DATABASE_AND_SFTP' }
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$report = Join-Path $OutputDirectory 'backup-restore-summary.json'
@{ result = 'RUNNING'; scope = $scope } | ConvertTo-Json | Set-Content $report -Encoding utf8
try {
    if (!$DatabaseOnly -and (!$RestoredSftpHost -or !$SftpHostFingerprint -or !$env:TIANSHU_RESTORE_SFTP_PASSWORD -or
            !$SftpBackupArchive -or !(Test-Path -LiteralPath $SftpBackupArchive -PathType Leaf))) {
        throw 'Full recovery needs the SFTP volume archive, restored endpoint, trusted fingerprint and TIANSHU_RESTORE_SFTP_PASSWORD. Use -DatabaseOnly for a database-only check.'
    }
    $tables = @(Invoke-MySql @('-N', '-B', '-e', "SELECT table_name FROM information_schema.tables WHERE table_schema='$Database' AND table_type='BASE TABLE' ORDER BY table_name;"))
    if (-not $tables.Count) { throw 'Source database has no base tables' }
    $before = Read-TableFacts $Database $tables
    Write-Host "Creating logical backup inside $DockerContainer..."
    $dumpStarted = $true
    Invoke-ContainerShell "mysqldump --single-transaction --routines --events --triggers --hex-blob $Database > $dumpPath"
    Invoke-MySql @('-e', "CREATE DATABASE $RestoreDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
    $restoreCreated = $true
    Write-Host "Restoring backup into $RestoreDatabase..."
    Invoke-ContainerShell "mysql $RestoreDatabase < $dumpPath"

    $after = Read-TableFacts $RestoreDatabase $tables
    $mismatches = @($tables | Where-Object { ($before[$_] | ConvertTo-Json -Compress) -ne ($after[$_] | ConvertTo-Json -Compress) })
    $storage = $null
    if (!$DatabaseOnly) {
        # HEX keeps Unicode reference names byte-exact through PowerShell's native pipeline.
        $sql = @"
SELECT 'model', HEX(model_content), LOWER(model_digest) FROM rule_model WHERE LEFT(model_content,11)='ts-file-v1:'
UNION ALL SELECT 'model-version', HEX(model_content), LOWER(model_digest) FROM rule_model_version WHERE LEFT(model_content,11)='ts-file-v1:'
UNION ALL SELECT 'snapshot', HEX(JSON_UNQUOTE(JSON_EXTRACT(execution_snapshot_json,'$.packageFile'))), LOWER(JSON_UNQUOTE(JSON_EXTRACT(execution_snapshot_json,'$.packageDigest'))) FROM rule_model_version WHERE JSON_CONTAINS_PATH(execution_snapshot_json,'one','$.packageFile')
UNION ALL SELECT 'artifact', HEX(package_content), LOWER(package_digest) FROM decision_artifact WHERE LEFT(package_content,11)=_binary'ts-file-v1:'
UNION ALL SELECT 'png', HEX(CONCAT('ledger/png/',f.png_digest)), f.png_digest FROM rule_ledger_asset a LEFT JOIN rule_ledger_asset_file f ON f.asset_id=a.id WHERE a.scan_status='CLEAN' AND a.content IS NULL
UNION ALL SELECT 'pending', HEX(CONCAT('ledger/pending/',id,'/',sha256)), sha256 FROM rule_ledger_asset WHERE scan_status<>'CLEAN' AND raw_content IS NULL;
"@
        $rows = @(Invoke-MySql @('-N','-B',$RestoreDatabase,'-e',$sql))
        $verification = $rows | & python (Join-Path $PSScriptRoot 'verify-restored-sftp.py') --archive $SftpBackupArchive --host $RestoredSftpHost --port $RestoredSftpPort --user $SftpUsername --fingerprint $SftpHostFingerprint
        if ($LASTEXITCODE -ne 0) { throw 'Cold read of restored SFTP or comparison with database and backup failed' }
        $storage = $verification | ConvertFrom-Json
    }
    $summary = [ordered]@{
        result = $(if ($mismatches.Count) { 'FAIL' } else { 'PASS' })
        scope = $scope
        sftp = $storage
        generatedAt = (Get-Date).ToUniversalTime().ToString('o')
        sourceDatabase = $Database
        restoreDatabase = $RestoreDatabase
        tableCount = $tables.Count
        mismatches = $mismatches
        before = $before
        after = $after
        durationSeconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 2)
    }
    $summary | ConvertTo-Json -Depth 6 | Set-Content $report -Encoding utf8
    Write-Output ($summary | ConvertTo-Json -Depth 6)
    if ($mismatches.Count) { throw ('Restored table content mismatch: ' + ($mismatches -join ', ')) }
}
catch {
    if (!$summary) {
        @{ result = 'FAIL'; scope = $scope; generatedAt = (Get-Date).ToUniversalTime().ToString('o') } |
            ConvertTo-Json | Set-Content $report -Encoding utf8
    }
    throw
}
finally {
    if ($restoreCreated -and !($KeepVerifiedDatabase -and $summary -and $summary.result -eq 'PASS')) {
        try { Invoke-MySql @('-e', "DROP DATABASE IF EXISTS $RestoreDatabase;") } catch { Write-Warning $_ }
    }
    if ($dumpStarted) {
        try { Invoke-ContainerShell "rm -f $dumpPath" } catch { Write-Warning $_ }
    }
}
