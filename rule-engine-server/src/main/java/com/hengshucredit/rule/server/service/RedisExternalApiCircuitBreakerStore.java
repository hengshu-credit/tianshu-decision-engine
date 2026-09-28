package com.hengshucredit.rule.server.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** Redis implementation of the external API circuit breaker state machine. */
@Component
public class RedisExternalApiCircuitBreakerStore implements ExternalApiCircuitBreakerDistributedStore {

    private static final DefaultRedisScript<Long> ACQUIRE = script(
            "redis/external-api-circuit-acquire.lua", Long.class);
    private static final DefaultRedisScript<Long> RECORD = script(
            "redis/external-api-circuit-record.lua", Long.class);
    private final StringRedisTemplate redisTemplate;

    public RedisExternalApiCircuitBreakerStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public AcquireResult acquire(Long apiConfigId, int failureRate, int minCalls,
                                 int windowSize, int openSeconds, int halfOpenCalls) {
        try {
            Long result = redisTemplate.execute(ACQUIRE,
                    java.util.Collections.singletonList(key(apiConfigId, "state")),
                    Integer.toString(failureRate), Integer.toString(minCalls),
                    Integer.toString(windowSize), Integer.toString(openSeconds),
                    Integer.toString(halfOpenCalls));
            if (result == null) throw new DistributedUnavailable("Redis circuit acquire 返回为空");
            return result == 2L ? AcquireResult.HALF_OPEN
                    : result == 1L ? AcquireResult.CLOSED : AcquireResult.OPEN;
        } catch (DistributedUnavailable e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DistributedUnavailable("Redis 外数 API 熔断器不可用", e);
        }
    }

    @Override
    public void record(Long apiConfigId, boolean halfOpen, boolean success, int failureRate,
                       int minCalls, int windowSize, int openSeconds, int halfOpenCalls) {
        try {
            redisTemplate.execute(RECORD,
                    Arrays.asList(key(apiConfigId, "state"), key(apiConfigId, "outcomes")),
                    halfOpen ? "1" : "0", success ? "1" : "0", Integer.toString(failureRate),
                    Integer.toString(minCalls), Integer.toString(windowSize),
                    Integer.toString(openSeconds), Integer.toString(halfOpenCalls));
        } catch (RuntimeException e) {
            throw new DistributedUnavailable("Redis 外数 API 熔断记录失败", e);
        }
    }

    private String key(Long id, String kind) {
        return "rule:external-api:circuit:{" + id + "}:" + kind;
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
