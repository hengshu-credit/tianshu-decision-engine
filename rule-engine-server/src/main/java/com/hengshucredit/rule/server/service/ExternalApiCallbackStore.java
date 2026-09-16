package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 以本次调用的随机 ID 隔离回调，Redis 支持回调落在其他服务实例。 */
@Service
public class ExternalApiCallbackStore {
    private static final String PREFIX = "rule:external-callback:";
    private final StringRedisTemplate redis;
    private final CredentialCipher cipher;

    public ExternalApiCallbackStore(StringRedisTemplate redis, CredentialCipher cipher) {
        this.redis = redis;
        this.cipher = cipher;
    }

    public String register(JSONObject config) {
        String id = UUID.randomUUID().toString();
        redis.opsForValue().set(PREFIX + id, cipher.encrypt(config.toJSONString()),
                Math.max(1, RequestDeadlineContext.remainingMillis()) + 30000L, TimeUnit.MILLISECONDS);
        return id;
    }

    public void accept(String id, HttpHeaders headers, byte[] body) {
        if (body.length > 1048576) throw new IllegalArgumentException("回调内容不能超过 1MB");
        String saved = redis.opsForValue().get(PREFIX + id);
        if (saved == null) throw new IllegalArgumentException("回调等待已结束或不存在");
        JSONObject config = JSON.parseObject(cipher.decrypt(saved));
        String signature = headers.getFirst(config.getString("signatureHeader"));
        if (!validSignature(config.getString("signatureSecret"), body, signature)) {
            throw new SecurityException("回调签名校验失败");
        }
        Object parsed = JSON.parse(new String(body, StandardCharsets.UTF_8));
        Map<String, Object> envelope = new java.util.LinkedHashMap<>();
        envelope.put("httpStatus", 200);
        envelope.put("body", parsed);
        Object status = path(envelope, config.getString("statusPath"));
        if (status == null) throw new IllegalArgumentException("回调缺少状态字段");
        if (!String.valueOf(status).equals(config.getString("successValue"))
                && !String.valueOf(status).equals(config.getString("failureValue"))) return;
        Long ttl = redis.getExpire(PREFIX + id, TimeUnit.MILLISECONDS);
        if (ttl == null || ttl <= 0) throw new IllegalArgumentException("回调等待已结束");
        redis.opsForValue().setIfAbsent(PREFIX + id + ":result", cipher.encrypt(JSON.toJSONString(envelope)),
                ttl, TimeUnit.MILLISECONDS);
    }

    public Map<String, Object> result(String id) {
        String value = redis.opsForValue().get(PREFIX + id + ":result");
        return value == null ? null : JSON.parseObject(cipher.decrypt(value));
    }

    public void close(String id) {
        redis.delete(List.of(PREFIX + id, PREFIX + id + ":result"));
    }

    static Object path(Object root, String path) {
        return path == null || path.isBlank() ? null : JSONPath.eval(root, path.startsWith("$") ? path : "$." + path);
    }

    static boolean validSignature(String secret, byte[] body, String supplied) {
        if (secret == null || secret.isBlank() || supplied == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String value = supplied.startsWith("sha256=") ? supplied.substring(7) : supplied;
            return MessageDigest.isEqual(mac.doFinal(body), HexFormat.of().parseHex(value));
        } catch (java.security.GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
