package com.hengshucredit.rule.server.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ProjectTokenRateLimiter {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ProjectAuthProperties properties;

    public boolean isAllowed(String clientIp, String credentialFingerprint) {
        try {
            return !hasKey(lockKey("ip", clientIp))
                    && !hasKey(lockKey("credential", credentialFingerprint));
        } catch (RuntimeException e) {
            log.error("Unable to read project token rate limit state", e);
            return false;
        }
    }

    public void recordFailure(String clientIp, String credentialFingerprint) {
        recordDimensionFailure("ip", clientIp);
        recordDimensionFailure("credential", credentialFingerprint);
    }

    public void clearFailures(String clientIp, String credentialFingerprint) {
        try {
            stringRedisTemplate.delete(Arrays.asList(
                    countKey("ip", clientIp), countKey("credential", credentialFingerprint)));
        } catch (RuntimeException e) {
            log.warn("Unable to clear project token failures", e);
        }
    }

    private void recordDimensionFailure(String dimension, String value) {
        try {
            String countKey = countKey(dimension, value);
            Long failures = stringRedisTemplate.opsForValue().increment(countKey);
            if (failures != null && failures == 1L) {
                stringRedisTemplate.expire(countKey, properties.getTokenFailureWindowSeconds(), TimeUnit.SECONDS);
            }
            if (failures != null && failures >= properties.getTokenFailureLimit()) {
                stringRedisTemplate.opsForValue().set(lockKey(dimension, value), "1",
                        properties.getTokenLockSeconds(), TimeUnit.SECONDS);
                stringRedisTemplate.delete(countKey);
            }
        } catch (RuntimeException e) {
            log.error("Unable to update project token rate limit state", e);
        }
    }

    private boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    private String countKey(String dimension, String value) {
        return "rule:auth:token:fail:" + dimension + ":" + value;
    }

    private String lockKey(String dimension, String value) {
        return "rule:auth:token:lock:" + dimension + ":" + value;
    }
}
