param(
    [string]$DockerContainer = "rule-engine-redis",
    [string]$RedisHost = "127.0.0.1",
    [int]$RedisPort = 6379,
    [string]$RedisCli = "redis-cli",
    [string]$RedisPassword = ""
)

$ErrorActionPreference = "Stop"
if ($DockerContainer -and -not (Get-Command "docker" -ErrorAction SilentlyContinue)) {
    throw 'docker CLI was not found'
}
if (-not $DockerContainer -and -not (Get-Command $RedisCli -ErrorAction SilentlyContinue)) {
    throw 'redis-cli was not found; pass -DockerContainer or install Redis CLI'
}

function Invoke-Redis([string[]]$Arguments) {
    $auth = if ($RedisPassword) { @("-a", $RedisPassword) } else { @() }
    if ($DockerContainer) {
        & docker exec $DockerContainer redis-cli -h $RedisHost -p $RedisPort @auth @Arguments
    } else {
        & $RedisCli -h $RedisHost -p $RedisPort @auth @Arguments
    }
}

function Eval([string]$Script, [string[]]$Keys, [string[]]$ScriptArgs) {
    $raw = @("--raw", "EVAL", $Script, [string]$Keys.Count) + $Keys + $ScriptArgs
    return @(Invoke-Redis $raw)
}

$guardState = 'rule:external-api:guard:{verify}:state'
$guardPermits = 'rule:external-api:guard:{verify}:permits'
$circuitState = 'rule:external-api:circuit:{verify}:state'
$circuitOutcomes = 'rule:external-api:circuit:{verify}:outcomes'
Invoke-Redis @("DEL", $guardState, $guardPermits, $circuitState, $circuitOutcomes) | Out-Null

$guardAcquire = Get-Content "rule-engine-server/src/main/resources/redis/external-api-guard-acquire.lua" -Raw
$guardRelease = Get-Content "rule-engine-server/src/main/resources/redis/external-api-guard-release.lua" -Raw
$first = Eval $guardAcquire @($guardState, $guardPermits) @("0", "1", "1", "5000", "permit-a")
if (($first -join '').Trim() -ne '1') { throw "external guard first acquire failed: $($first -join ',')" }
$second = Eval $guardAcquire @($guardState, $guardPermits) @("0", "1", "1", "5000", "permit-b")
if (($second -join '').Trim() -ne '-2') { throw "external guard concurrency limit failed: $($second -join ',')" }
$released = Eval $guardRelease @($guardPermits) @("permit-a")
if (($released -join '').Trim() -ne '1') { throw "external guard release failed: $($released -join ',')" }
$releasedAgain = Eval $guardRelease @($guardPermits) @("permit-a")
if (($releasedAgain -join '').Trim() -ne '0') { throw "external guard release is not idempotent: $($releasedAgain -join ',')" }

Invoke-Redis @("DEL", $guardState, $guardPermits) | Out-Null
$rateFirst = Eval $guardAcquire @($guardState, $guardPermits) @("1", "1", "100", "5000", "rate-a")
if (($rateFirst -join '').Trim() -ne '1') { throw "external guard first QPS token failed: $($rateFirst -join ',')" }
$rateSecond = Eval $guardAcquire @($guardState, $guardPermits) @("1", "1", "100", "5000", "rate-b")
if (($rateSecond -join '').Trim() -ne '-1') { throw "external guard QPS limit failed: $($rateSecond -join ',')" }
Start-Sleep -Milliseconds 1100
$rateThird = Eval $guardAcquire @($guardState, $guardPermits) @("1", "1", "100", "5000", "rate-c")
if (($rateThird -join '').Trim() -ne '1') { throw "external guard QPS refill failed: $($rateThird -join ',')" }

$circuitAcquire = Get-Content "rule-engine-server/src/main/resources/redis/external-api-circuit-acquire.lua" -Raw
$circuitRecord = Get-Content "rule-engine-server/src/main/resources/redis/external-api-circuit-record.lua" -Raw
Invoke-Redis @("DEL", $circuitState, $circuitOutcomes) | Out-Null
$c1 = Eval $circuitAcquire @($circuitState) @("50", "2", "2", "1", "1")
if (($c1 -join '').Trim() -ne '1') { throw "external circuit first acquire failed: $($c1 -join ',')" }
Eval $circuitRecord @($circuitState, $circuitOutcomes) @("0", "0", "50", "2", "2", "1", "1") | Out-Null
$c2 = Eval $circuitAcquire @($circuitState) @("50", "2", "2", "1", "1")
if (($c2 -join '').Trim() -ne '1') { throw "external circuit second acquire failed: $($c2 -join ',')" }
Eval $circuitRecord @($circuitState, $circuitOutcomes) @("0", "0", "50", "2", "2", "1", "1") | Out-Null
$c3 = Eval $circuitAcquire @($circuitState) @("50", "2", "2", "1", "1")
if (($c3 -join '').Trim() -ne '0') { throw "external circuit did not open: $($c3 -join ',')" }
Start-Sleep -Milliseconds 1100
$half = Eval $circuitAcquire @($circuitState) @("50", "2", "2", "1", "1")
if (($half -join '').Trim() -ne '2') { throw "external circuit did not enter half-open: $($half -join ',')" }
Eval $circuitRecord @($circuitState, $circuitOutcomes) @("1", "1", "50", "2", "2", "1", "1") | Out-Null
$closed = Eval $circuitAcquire @($circuitState) @("50", "2", "2", "1", "1")
if (($closed -join '').Trim() -ne '1') { throw "external circuit did not close after half-open success: $($closed -join ',')" }

Invoke-Redis @("DEL", $guardState, $guardPermits, $circuitState, $circuitOutcomes) | Out-Null
Write-Output "Redis external API guard verification passed: atomic concurrency, QPS refill, idempotent release, circuit open and half-open recovery."
