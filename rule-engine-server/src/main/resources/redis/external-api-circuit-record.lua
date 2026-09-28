local key = KEYS[1]
local outcomes_key = KEYS[2]
local now = redis.call('TIME')
local now_ms = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
local half_open = tonumber(ARGV[1]) == 1
local success = tonumber(ARGV[2]) == 1
local failure_rate = tonumber(ARGV[3])
local min_calls = tonumber(ARGV[4])
local window_size = tonumber(ARGV[5])
local open_seconds = tonumber(ARGV[6])
local half_open_calls = tonumber(ARGV[7])

local state = redis.call('hget', key, 'state') or 'CLOSED'
if half_open then
    local in_flight = tonumber(redis.call('hget', key, 'half_open_in_flight')) or 0
    if in_flight > 0 then redis.call('hincrby', key, 'half_open_in_flight', -1) end
    if not success then
        redis.call('hset', key, 'state', 'OPEN', 'open_until', now_ms + open_seconds * 1000,
            'half_open_in_flight', 0, 'half_open_successes', 0)
    else
        local successes = redis.call('hincrby', key, 'half_open_successes', 1)
        if successes >= half_open_calls then
            redis.call('hset', key, 'state', 'CLOSED', 'open_until', 0,
                'half_open_in_flight', 0, 'half_open_successes', 0)
            redis.call('del', outcomes_key)
        end
    end
    redis.call('pexpire', key, math.max(60000, open_seconds * 1000))
    return 1
end
if state ~= 'CLOSED' then return 0 end
redis.call('lpush', outcomes_key, success and '1' or '0')
redis.call('ltrim', outcomes_key, 0, window_size - 1)
redis.call('pexpire', outcomes_key, math.max(60000, open_seconds * 1000))
local outcomes = redis.call('lrange', outcomes_key, 0, window_size - 1)
if #outcomes < min_calls then return 1 end
local failures = 0
for _, outcome in ipairs(outcomes) do
    if outcome == '0' then failures = failures + 1 end
end
if failures * 100 >= failure_rate * #outcomes then
    redis.call('hset', key, 'state', 'OPEN', 'open_until', now_ms + open_seconds * 1000,
        'half_open_in_flight', 0, 'half_open_successes', 0)
    redis.call('pexpire', key, math.max(60000, open_seconds * 1000))
end
return 1
