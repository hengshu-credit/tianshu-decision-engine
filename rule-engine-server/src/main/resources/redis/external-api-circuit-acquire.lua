local key = KEYS[1]
local now = redis.call('TIME')
local now_ms = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
local open_until = tonumber(redis.call('hget', key, 'open_until')) or 0
local state = redis.call('hget', key, 'state') or 'CLOSED'
local half_open_calls = tonumber(ARGV[5])

if state == 'OPEN' then
    if now_ms < open_until then return 0 end
    state = 'HALF_OPEN'
    redis.call('hset', key, 'state', state, 'half_open_in_flight', 0, 'half_open_successes', 0)
end
if state == 'HALF_OPEN' then
    local in_flight = tonumber(redis.call('hget', key, 'half_open_in_flight')) or 0
    if in_flight >= half_open_calls then return 0 end
    redis.call('hincrby', key, 'half_open_in_flight', 1)
    redis.call('pexpire', key, math.max(60000, tonumber(ARGV[4]) * 1000))
    return 2
end
redis.call('hset', key, 'state', 'CLOSED')
redis.call('pexpire', key, math.max(60000, tonumber(ARGV[4]) * 1000))
return 1
