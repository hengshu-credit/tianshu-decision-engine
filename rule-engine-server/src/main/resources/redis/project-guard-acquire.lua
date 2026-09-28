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
    if redis.call('ZCARD', KEYS[2]) >= maxConcurrent then
        return {0, 'CONCURRENT_LIMITED'}
    end
end

if qps > 0 then
    local tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens'))
    local last = tonumber(redis.call('HGET', KEYS[1], 'last'))
    if tokens == nil then tokens = burst end
    if last == nil then last = now end
    local elapsed = math.max(0, now - last)
    tokens = math.min(burst, tokens + elapsed * qps / 1000.0)
    if tokens < 1 then
        redis.call('HSET', KEYS[1], 'tokens', tokens, 'last', now)
        redis.call('PEXPIRE', KEYS[1], math.max(60000, leaseMillis * 2))
        return {0, 'RATE_LIMITED'}
    end
    redis.call('HSET', KEYS[1], 'tokens', tokens - 1, 'last', now)
    redis.call('PEXPIRE', KEYS[1], math.max(60000, leaseMillis * 2))
end

local hardExpiry = hardLifetime > 0 and now + hardLifetime or 0
local expiry = now + leaseMillis
if hardExpiry > 0 and expiry > hardExpiry then expiry = hardExpiry end
if maxConcurrent > 0 then
    redis.call('ZADD', KEYS[2], expiry, permitId)
    redis.call('PEXPIRE', KEYS[2], math.max(leaseMillis, 1000))
end
return {1, now, expiry, hardExpiry}
