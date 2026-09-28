package com.hengshucredit.rule.server.auth;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/** Redis-backed authId QPS and concurrency guard. */
@Component
public class RedisProjectExecutionGuardStore implements ProjectExecutionGuardStore {

    private static final long DEFAULT_LEASE_MILLIS = 30_000L;
    private static final long MAX_LEASE_MILLIS = 600_000L;
    private static final long MIN_LEASE_MILLIS = 1_000L;

    private static final DefaultRedisScript<List> ACQUIRE_SCRIPT = script(
            "redis/project-guard-acquire.lua", List.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = script(
            "redis/project-guard-renew.lua", Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = script(
            "redis/project-guard-release.lua", Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisProjectExecutionGuardStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public AcquireResult acquire(Long authId, ProjectAccessPolicy policy, String permitId) {
        if (authId == null || policy == null || permitId == null) {
            throw new IllegalArgumentException("authId、policy、permitId 不能为空");
        }
        long leaseMillis = leaseMillis(policy.getRequestTimeoutMs());
        // 即使业务没有配置 requestTimeout，也给 permit 一个硬上限；续租只能在该
        // 上限内延长，防止一个失控请求无限占用全局并发名额。
        long hardLifetime = Math.min(MAX_LEASE_MILLIS,
                policy.getRequestTimeoutMs() > 0
                        ? Math.max(leaseMillis, policy.getRequestTimeoutMs() + 1000L)
                        : MAX_LEASE_MILLIS);
        try {
            List<?> result = redisTemplate.execute(ACQUIRE_SCRIPT,
                    keys(authId, "bucket", "permits"),
                    String.valueOf(policy.getQps()), String.valueOf(policy.getBurst()),
                    String.valueOf(policy.getMaxConcurrent()), permitId,
                    String.valueOf(leaseMillis), String.valueOf(hardLifetime));
            if (result == null || result.isEmpty()) throw new IllegalStateException("Redis 执行保护脚本返回为空");
            if (number(result.get(0)) == 0L) {
                String reason = result.size() > 1 ? String.valueOf(result.get(1)) : "RATE_LIMITED";
                return AcquireResult.rejected("CONCURRENT_LIMITED".equals(reason)
                        ? ProjectExecutionGuard.Reason.CONCURRENT_LIMITED
                        : ProjectExecutionGuard.Reason.RATE_LIMITED);
            }
            long hardExpiry = result.size() > 3 ? number(result.get(3)) : 0L;
            long expiry = result.size() > 2 ? number(result.get(2)) : 0L;
            long effectiveLease = policy.getMaxConcurrent() > 0 && expiry > 0 && result.size() > 1
                    ? Math.max(MIN_LEASE_MILLIS, expiry - number(result.get(1))) : 0L;
            return AcquireResult.allowed(effectiveLease, hardExpiry);
        } catch (ProjectExecutionGuardStore.DistributedUnavailable e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DistributedUnavailable(e);
        }
    }

    @Override
    public boolean renew(Long authId, String permitId, long leaseMillis, long hardExpiryMillis) {
        try {
            Long value = redisTemplate.execute(RENEW_SCRIPT,
                    Collections.singletonList(key(authId, "permits")), permitId,
                    String.valueOf(leaseMillis), String.valueOf(hardExpiryMillis));
            return Long.valueOf(1L).equals(value);
        } catch (RuntimeException e) {
            throw new DistributedUnavailable(e);
        }
    }

    @Override
    public boolean release(Long authId, String permitId) {
        try {
            Long value = redisTemplate.execute(RELEASE_SCRIPT,
                    Collections.singletonList(key(authId, "permits")), permitId);
            return Long.valueOf(1L).equals(value);
        } catch (RuntimeException e) {
            throw new DistributedUnavailable(e);
        }
    }

    private long leaseMillis(int requestTimeoutMs) {
        if (requestTimeoutMs <= 0) return DEFAULT_LEASE_MILLIS;
        return Math.min(MAX_LEASE_MILLIS, Math.max(MIN_LEASE_MILLIS, requestTimeoutMs + 1000L));
    }

    private List<String> keys(Long authId, String bucket, String permits) {
        return java.util.Arrays.asList(key(authId, bucket), key(authId, permits));
    }

    private String key(Long authId, String kind) {
        return "rule:auth:guard:{" + authId + "}:" + kind;
    }

    private long number(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static <T> DefaultRedisScript<T> script(String location, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(resultType);
        return script;
    }
}
