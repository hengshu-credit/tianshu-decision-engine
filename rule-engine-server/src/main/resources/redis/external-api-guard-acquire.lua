local now = redis.call('TIME')
local now_ms = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
local state_key = KEYS[1]
local permits_key = KEYS[2]
local qps = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local max_concurrent = tonumber(ARGV[3])
local lease_ms = tonumber(ARGV[4])
local permit_id = ARGV[5]

redis.call('zremrangebyscore', permits_key, '-inf', now_ms)
local active = tonumber(redis.call('zcard', permits_key))
if active >= max_concurrent then
    return -2
end

local tokens = tonumber(redis.call('hget', state_key, 'tokens'))
local last_ms = tonumber(redis.call('hget', state_key, 'last_ms'))
if tokens == nil then tokens = capacity end
if last_ms == nil then last_ms = now_ms end
if qps > 0 then
    tokens = math.min(capacity, tokens + (now_ms - last_ms) * qps / 1000.0)
    if tokens < 1 then
        redis.call('hset', state_key, 'tokens', tokens, 'last_ms', now_ms)
        redis.call('pexpire', state_key, math.max(lease_ms, 60000))
        return -1
    end
    tokens = tokens - 1
else
    tokens = capacity
end

redis.call('hset', state_key, 'tokens', tokens, 'last_ms', now_ms)
redis.call('pexpire', state_key, math.max(lease_ms, 60000))
redis.call('zadd', permits_key, now_ms + lease_ms, permit_id)
redis.call('pexpire', permits_key, math.max(lease_ms, 60000))
return 1
