package com.hengshucredit.rule.server.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;

/** Redis implementation of the external API token bucket and concurrency lease. */
@Component
public class RedisExternalApiGuardStore implements ExternalApiGuardDistributedStore {

    private static final long MIN_LEASE_MILLIS = 1000L;
    private static final long MAX_LEASE_MILLIS = 600_000L;
    private static final DefaultRedisScript<Long> ACQUIRE = script(
            "redis/external-api-guard-acquire.lua", Long.class);
    private static final DefaultRedisScript<Long> RELEASE = script(
            "redis/external-api-guard-release.lua", Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisExternalApiGuardStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public AcquireResult tryAcquire(Long apiConfigId, double qps, int burstCapacity,
                                    int maxConcurrent, long leaseMillis, String permitId) {
        if (apiConfigId == null || permitId == null) {
            throw new IllegalArgumentException("apiConfigId、permitId 不能为空");
        }
        long lease = Math.max(MIN_LEASE_MILLIS, Math.min(MAX_LEASE_MILLIS, leaseMillis));
        try {
            Long result = redisTemplate.execute(ACQUIRE,
                    Arrays.asList(key(apiConfigId, "state"), key(apiConfigId, "permits")),
                    Double.toString(Math.max(0D, qps)), Integer.toString(Math.max(1, burstCapacity)),
                    Integer.toString(Math.max(1, maxConcurrent)), Long.toString(lease), permitId);
            if (result == null) throw new DistributedUnavailable("Redis guard acquire 返回为空");
            if (result == 1L) return AcquireResult.ACQUIRED;
            return result == -1L ? AcquireResult.RATE_LIMITED : AcquireResult.CONCURRENCY_LIMITED;
        } catch (DistributedUnavailable e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DistributedUnavailable("Redis 外数 API 守卫不可用", e);
        }
    }

    @Override
    public void release(Long apiConfigId, String permitId) {
        try {
            redisTemplate.execute(RELEASE,
                    Collections.singletonList(key(apiConfigId, "permits")), permitId);
        } catch (RuntimeException e) {
            throw new DistributedUnavailable("Redis 外数 API 守卫释放失败", e);
        }
    }

    private String key(Long id, String kind) {
        return "rule:external-api:guard:{" + id + "}:" + kind;
    }

    private static <T> DefaultRedisScript<T> script(String location, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(resultType);
        return script;
    }

    public static class DistributedUnavailable extends IllegalStateException {
        public DistributedUnavailable(String message) { super(message); }
        public DistributedUnavailable(String message, Throwable cause) { super(message, cause); }
    }
}
