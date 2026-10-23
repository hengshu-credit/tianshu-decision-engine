package com.hengshucredit.rule.server.consolelogin;

import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.PlatformException;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Locale;

/** 多节点共用计数；Redis 不可用时不放行凭证尝试或发信。 */
@Service
public class ConsoleAuthRateLimiter {
    private static final DefaultRedisScript<Long> ATTEMPT = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n", Long.class);
    @Resource private StringRedisTemplate redis;
    @org.springframework.beans.factory.annotation.Value("${spring.session.redis.namespace:spring:session}")
    private String namespace = "spring:session";

    public void check(String action, String identifier, String ip) {
        boolean mail = action.contains("mail") || action.equals("register");
        int window = mail ? 900 : 300;
        int identityLimit = mail ? 5 : 10;
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        try {
            if (attempt(key(action, "ip", ip), window) > 30
                    || (!normalized.isEmpty() && attempt(key(action, "identity", normalized), window) > identityLimit)) {
                throw new PlatformException(PlatformCode.REQUEST_TOO_FREQUENT, "操作次数过多，请稍后重试");
            }
        } catch (PlatformException e) { throw e; }
        catch (RuntimeException e) { throw new PlatformException(PlatformCode.SERVICE_UNAVAILABLE, "账户安全校验暂不可用，请稍后重试", e); }
    }

    protected long attempt(String key, int seconds) {
        Long count = redis.execute(ATTEMPT, List.of(key), String.valueOf(seconds));
        if (count == null) throw new IllegalStateException("安全计数未返回结果");
        return count;
    }

    private String key(String action, String dimension, String value) {
        return namespace + ":console:attempt:" + action + ":" + dimension + ":"
                + com.hengshucredit.rule.server.artifact.Sha256Digests.text(value == null ? "" : value);
    }
}
