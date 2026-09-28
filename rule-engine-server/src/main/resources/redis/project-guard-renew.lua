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
