package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.auth.CredentialCipher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Redis 中的 SFTP 文件缓存，内容使用项目主密钥加密后再保存。 */
@Component
public class RedisSftpFileCacheStore implements SftpFileDistributedCache {
    private static final String PREFIX = "rule:sftp-file-cache:";

    private final StringRedisTemplate redisTemplate;
    private final CredentialCipher credentialCipher;

    public RedisSftpFileCacheStore(StringRedisTemplate redisTemplate, CredentialCipher credentialCipher) {
        this.redisTemplate = redisTemplate;
        this.credentialCipher = credentialCipher;
    }

    @Override
    public CachedFile get(String cacheKey) {
        String key = key(cacheKey);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return null;
        int separator = value.indexOf(':');
        if (separator <= 0) return null;
        try {
            long expiresAt = Long.parseLong(value.substring(0, separator));
            if (expiresAt <= System.currentTimeMillis()) {
                redisTemplate.delete(key);
                return null;
            }
            byte[] content = Base64.getDecoder().decode(
                    credentialCipher.decrypt(value.substring(separator + 1)));
            return new CachedFile(content, expiresAt);
        } catch (RuntimeException failure) {
            redisTemplate.delete(key);
            return null;
        }
    }

    @Override
    public void put(String cacheKey, CachedFile value) {
        if (value == null || value.content() == null) return;
        long ttlMillis = value.expiresAtMillis() - System.currentTimeMillis();
        if (ttlMillis <= 0) return;
        String redisKey = key(cacheKey);
        String payload = Base64.getEncoder().encodeToString(value.content());
        String encoded = value.expiresAtMillis() + ":" + credentialCipher.encrypt(payload);
        redisTemplate.opsForValue().set(redisKey, encoded, ttlMillis, TimeUnit.MILLISECONDS);
        Long datasourceId = datasourceId(cacheKey);
        if (datasourceId != null) {
            String index = indexKey(datasourceId);
            redisTemplate.opsForSet().add(index, redisKey);
            redisTemplate.expire(index, ttlMillis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void invalidateDatasource(Long datasourceId) {
        if (datasourceId == null) return;
        String index = indexKey(datasourceId);
        Set<String> keys = redisTemplate.opsForSet().members(index);
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        redisTemplate.delete(index);
    }

    private String key(String cacheKey) {
        return PREFIX + sha256(cacheKey);
    }

    private String indexKey(Long datasourceId) {
        return PREFIX + "datasource:" + datasourceId + ":index";
    }

    private Long datasourceId(String cacheKey) {
        if (cacheKey == null || !cacheKey.startsWith("SFTP:")) return null;
        int separator = cacheKey.indexOf(':', 5);
        if (separator <= 5) return null;
        try {
            return Long.parseLong(cacheKey.substring(5, separator));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SFTP Redis缓存键摘要计算失败", e);
        }
    }
}
