param(
    [string]$RedisHost = "127.0.0.1",
    [int]$RedisPort = 6379,
    [string]$RedisCli = "redis-cli",
    [string]$DockerContainer = "",
    [string]$RedisPassword = ""
)

$ErrorActionPreference = "Stop"

if ($DockerContainer -and -not (Get-Command "docker" -ErrorAction SilentlyContinue)) {
    throw 'docker CLI was not found; remove -DockerContainer or install Docker CLI'
}
if (-not $DockerContainer -and -not (Get-Command $RedisCli -ErrorAction SilentlyContinue)) {
    throw 'redis-cli was not found; install Redis CLI or pass -RedisCli'
}

$bucket = 'rule:auth:guard:{codex-verify}:bucket'
$permits = 'rule:auth:guard:{codex-verify}:permits'
$permitA = "permit-a"
$permitB = "permit-b"

function Invoke-Redis([string[]]$Arguments) {
    $auth = if ($RedisPassword) { @("-a", $RedisPassword) } else { @() }
    if ($DockerContainer) {
        & docker exec $DockerContainer redis-cli -h $RedisHost -p $RedisPort @auth @Arguments
    } else {
        & $RedisCli -h $RedisHost -p $RedisPort @auth @Arguments
    }
}

Invoke-Redis @("DEL", $bucket, $permits) | Out-Null

$acquireScript = @'
local nowParts = redis.call('TIME')
local now = tonumber(nowParts[1]) * 1000 + math.floor(tonumber(nowParts[2]) / 1000)
local qps = tonumber(ARGV[1])
local burst = tonumber(ARGV[2])
local maxConcurrent = tonumber(ARGV[3])
local permitId = ARGV[4]
local leaseMillis = tonumber(ARGV[5])
local hardLifetime = tonumber(ARGV[6])
if maxConcurrent > 0 then
  redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
  if redis.call('ZCARD', KEYS[2]) >= maxConcurrent then return {0, 'CONCURRENT_LIMITED'} end
end
if qps > 0 then
  local tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens')) or burst
  local last = tonumber(redis.call('HGET', KEYS[1], 'last')) or now
  tokens = math.min(burst, tokens + math.max(0, now - last) * qps / 1000.0)
  if tokens < 1 then return {0, 'RATE_LIMITED'} end
  redis.call('HSET', KEYS[1], 'tokens', tokens - 1, 'last', now)
end
local hardExpiry = hardLifetime > 0 and now + hardLifetime or 0
local expiry = now + leaseMillis
if hardExpiry > 0 and expiry > hardExpiry then expiry = hardExpiry end
if maxConcurrent > 0 then redis.call('ZADD', KEYS[2], expiry, permitId) end
return {1, expiry, hardExpiry}
'@

$first = @(Invoke-Redis @("--raw", "EVAL", $acquireScript, "2", $bucket, $permits, "0", "0", "1", $permitA, "5000", "5000"))
if ($first[0] -ne "1") { throw "first permit was not accepted: $($first -join ',')" }
$second = @(Invoke-Redis @("--raw", "EVAL", $acquireScript, "2", $bucket, $permits, "0", "0", "1", $permitB, "5000", "5000"))
if ($second[0] -ne "0" -or $second[1] -ne "CONCURRENT_LIMITED") {
    throw "concurrency limit failed: $($second -join ',')"
}

$releaseScript = "return redis.call('ZREM', KEYS[1], ARGV[1])"
$release = Invoke-Redis @("--raw", "EVAL", $releaseScript, "1", $permits, $permitA)
if ($release -ne "1") { throw "permit release failed: $release" }
$releaseAgain = Invoke-Redis @("--raw", "EVAL", $releaseScript, "1", $permits, $permitA)
if ($releaseAgain -ne "0") { throw "permit release is not idempotent: $releaseAgain" }

$afterRelease = @(Invoke-Redis @("--raw", "EVAL", $acquireScript, "2", $bucket, $permits, "0", "0", "1", $permitB, "5000", "5000"))
if ($afterRelease[0] -ne "1") { throw "permit was not reacquired after release: $($afterRelease -join ',')" }

$renewScript = @'
local expiry = tonumber(redis.call('ZSCORE', KEYS[1], ARGV[1]))
if not expiry then return 0 end
local nowParts = redis.call('TIME')
local now = tonumber(nowParts[1]) * 1000 + math.floor(tonumber(nowParts[2]) / 1000)
if expiry <= now then return 0 end
local nextExpiry = now + tonumber(ARGV[2])
local hardExpiry = tonumber(ARGV[3])
if hardExpiry > 0 and nextExpiry > hardExpiry then nextExpiry = hardExpiry end
if nextExpiry <= now then return 0 end
redis.call('ZADD', KEYS[1], nextExpiry, ARGV[1])
redis.call('PEXPIRE', KEYS[1], math.max(tonumber(ARGV[2]), 1000))
return 1
'@
$renewed = Invoke-Redis @("--raw", "EVAL", $renewScript, "1", $permits, $permitB, "5000", "0")
if ($renewed -ne "1") { throw "permit renewal failed: $renewed" }

Invoke-Redis @("DEL", $bucket, $permits) | Out-Null
$rateFirst = @(Invoke-Redis @("--raw", "EVAL", $acquireScript, "2", $bucket, $permits, "1", "1", "0", "rate-a", "5000", "5000"))
if ($rateFirst[0] -ne "1") { throw "first QPS token was not accepted: $($rateFirst -join ',')" }
$rateSecond = @(Invoke-Redis @("--raw", "EVAL", $acquireScript, "2", $bucket, $permits, "1", "1", "0", "rate-b", "5000", "5000"))
if ($rateSecond[0] -ne "0" -or $rateSecond[1] -ne "RATE_LIMITED") {
    throw "QPS limit failed: $($rateSecond -join ',')"
}
Start-Sleep -Milliseconds 1100
$rateThird = @(Invoke-Redis @("--raw", "EVAL", $acquireScript, "2", $bucket, $permits, "1", "1", "0", "rate-c", "5000", "5000"))
if ($rateThird[0] -ne "1") { throw "QPS refill failed: $($rateThird -join ',')" }

Invoke-Redis @("DEL", $bucket, $permits) | Out-Null
Write-Output "Redis guard Lua verification passed: TIME, atomic concurrency, QPS refill, renewal, and idempotent release."
