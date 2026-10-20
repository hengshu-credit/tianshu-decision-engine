package com.hengshucredit.rule.server.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ProjectAuthReplayGuard {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public boolean claim(Long authId, String nonce, long windowSeconds) {
        try {
            Boolean claimed = stringRedisTemplate.opsForValue().setIfAbsent(
                    "rule:auth:hmac:nonce:" + authId + ":" + nonce,
                    "1", windowSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(claimed);
        } catch (RuntimeException e) {
            log.error("Unable to verify HMAC nonce replay state", e);
            return false;
        }
    }

    public boolean claimGlobal(String keyId, String nonce, long windowSeconds) {
        try {
            String fingerprint = HmacRequestSigner.sha256Hex(
                    (String.valueOf(keyId) + ":" + nonce).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Boolean claimed = stringRedisTemplate.opsForValue().setIfAbsent(
                    "rule:auth:global:nonce:" + fingerprint,
                    "1", windowSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(claimed);
        } catch (RuntimeException e) {
            log.error("Unable to verify global access nonce replay state", e);
            return false;
        }
    }
}
