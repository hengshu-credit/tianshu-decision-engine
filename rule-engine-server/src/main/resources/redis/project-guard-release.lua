return redis.call('ZREM', KEYS[1], ARGV[1])
