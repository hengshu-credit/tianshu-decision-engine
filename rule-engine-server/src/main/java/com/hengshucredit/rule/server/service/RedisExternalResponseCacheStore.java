package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RedisExternalResponseCacheStore implements ExternalResponseDistributedStore {

    private static final String PREFIX = "rule:external-response-cache:";
    private final StringRedisTemplate redisTemplate;
    private final CredentialCipher credentialCipher;

    public RedisExternalResponseCacheStore(StringRedisTemplate redisTemplate, CredentialCipher credentialCipher) {
        this.redisTemplate = redisTemplate;
        this.credentialCipher = credentialCipher;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExternalApiResponseCache.CachedResponse get(Long apiConfigId, String cacheKey) {
        String value = redisTemplate.opsForValue().get(key(apiConfigId, cacheKey));
        if (value == null) return null;
        int first = value.indexOf(':');
        int second = first < 0 ? -1 : value.indexOf(':', first + 1);
        if (first <= 0 || second <= first) return null;
        try {
            long expiresAt = Long.parseLong(value.substring(0, first));
            long staleUntil = Long.parseLong(value.substring(first + 1, second));
            if (staleUntil < System.currentTimeMillis()) return null;
            String json = credentialCipher.decrypt(value.substring(second + 1));
            return new ExternalApiResponseCache.CachedResponse(
                    JSON.parseObject(json, LinkedHashMap.class), expiresAt, staleUntil);
        } catch (RuntimeException e) {
            redisTemplate.delete(key(apiConfigId, cacheKey));
            return null;
        }
    }

    @Override
    public void put(Long apiConfigId, String cacheKey, ExternalApiResponseCache.CachedResponse response) {
        long ttlMillis = response.getStaleUntilMillis() - System.currentTimeMillis();
        if (ttlMillis <= 0) return;
        String value = response.getExpiresAtMillis() + ":" + response.getStaleUntilMillis() + ":"
                + credentialCipher.encrypt(JSON.toJSONString(response.getResponse()));
        String entryKey = key(apiConfigId, cacheKey);
        redisTemplate.opsForValue().set(entryKey, value, ttlMillis, TimeUnit.MILLISECONDS);
        redisTemplate.opsForSet().add(indexKey(apiConfigId), entryKey);
        redisTemplate.expire(indexKey(apiConfigId), ttlMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void invalidateApi(Long apiConfigId) {
        Set<String> keys = redisTemplate.opsForSet().members(indexKey(apiConfigId));
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        redisTemplate.delete(indexKey(apiConfigId));
    }

    private String key(Long apiConfigId, String cacheKey) {
        return PREFIX + apiConfigId + ":" + sha256(cacheKey);
    }

    private String indexKey(Long apiConfigId) {
        return PREFIX + apiConfigId + ":index";
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
