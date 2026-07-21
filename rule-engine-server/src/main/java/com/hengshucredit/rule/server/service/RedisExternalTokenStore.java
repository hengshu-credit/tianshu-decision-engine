package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.auth.CredentialCipher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class RedisExternalTokenStore implements ExternalTokenDistributedStore {

    private static final String CACHE_PREFIX = "rule:external-token:cache:";
    private static final String LOCK_PREFIX = "rule:external-token:lock:";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final CredentialCipher credentialCipher;

    public RedisExternalTokenStore(StringRedisTemplate redisTemplate, CredentialCipher credentialCipher) {
        this.redisTemplate = redisTemplate;
        this.credentialCipher = credentialCipher;
    }

    @Override
    public ExternalTokenCache.CachedToken get(String cacheKey) {
        String value = redisTemplate.opsForValue().get(cacheKey(cacheKey));
        if (value == null) {
            return null;
        }
        int firstSeparator = value.indexOf(':');
        int secondSeparator = firstSeparator < 0 ? -1 : value.indexOf(':', firstSeparator + 1);
        if (firstSeparator <= 0 || secondSeparator <= firstSeparator) {
            redisTemplate.delete(cacheKey(cacheKey));
            return null;
        }
        try {
            long expiresAt = Long.parseLong(value.substring(0, firstSeparator));
            long usableExpiresAt = Long.parseLong(value.substring(firstSeparator + 1, secondSeparator));
            if (expiresAt <= System.currentTimeMillis()) {
                redisTemplate.delete(cacheKey(cacheKey));
                return null;
            }
            String token = credentialCipher.decrypt(value.substring(secondSeparator + 1));
            return new ExternalTokenCache.CachedToken(token, expiresAt, usableExpiresAt);
        } catch (RuntimeException e) {
            redisTemplate.delete(cacheKey(cacheKey));
            return null;
        }
    }

    @Override
    public void put(String cacheKey, ExternalTokenCache.CachedToken token) {
        long ttlMillis = token.getExpiresAtMillis() - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            return;
        }
        String payload = token.getExpiresAtMillis() + ":" + token.getUsableExpiresAtMillis()
                + ":" + credentialCipher.encrypt(token.getToken());
        redisTemplate.opsForValue().set(cacheKey(cacheKey), payload, ttlMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void invalidate(String cacheKey) {
        redisTemplate.delete(cacheKey(cacheKey));
    }

    @Override
    public boolean tryLock(String cacheKey, String owner, long lockSeconds) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                lockKey(cacheKey), owner, Math.max(1L, lockSeconds), TimeUnit.SECONDS));
    }

    @Override
    public void unlock(String cacheKey, String owner) {
        redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey(cacheKey)), owner);
    }

    private String cacheKey(String sourceKey) {
        return CACHE_PREFIX + sha256(sourceKey);
    }

    private String lockKey(String sourceKey) {
        return LOCK_PREFIX + sha256(sourceKey);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
