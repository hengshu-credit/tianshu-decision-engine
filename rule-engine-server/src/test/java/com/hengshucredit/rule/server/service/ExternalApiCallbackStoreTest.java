package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import org.junit.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ExternalApiCallbackStoreTest {
    static ExternalApiCallbackStore store() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setMasterKeys(Map.of("v1", "unit-test-encryption-key-0123456789abcdef"));
        return new ExternalApiCallbackStore(new MemoryRedis(), new CredentialCipher(properties));
    }

    static JSONObject protocol() {
        return JSON.parseObject("{\"taskIdPath\":\"body.job\",\"statusPath\":\"body.status\","
                + "\"successValue\":\"DONE\",\"failureValue\":\"FAILED\",\"resultPath\":\"body.report\","
                + "\"submissionTaskIdPath\":\"body.taskId\",\"signatureHeader\":\"X-Signature\",\"signatureSecret\":\"test-secret\"}");
    }

    static HttpHeaders signature(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Signature", java.util.HexFormat.of().formatHex(mac.doFinal(body)));
        return headers;
    }

    @Test
    public void callbackVerifiesSignatureIsolatesInvocationsAndKeepsFirstTerminalResult() throws Exception {
        ExternalApiCallbackStore store = store();
        String first = store.register(protocol());
        String second = store.register(protocol());
        byte[] body = "{\"job\":\"t1\",\"status\":\"DONE\",\"report\":{\"score\":720}}".getBytes(StandardCharsets.UTF_8);
        assertThrows(SecurityException.class, () -> store.accept(first, new HttpHeaders(), body));
        assertNull(store.result(first));
        store.accept(first, signature(body), body);
        assertNull(store.result(second));
        byte[] duplicate = "{\"job\":\"t1\",\"status\":\"DONE\",\"report\":{\"score\":1}}".getBytes(StandardCharsets.UTF_8);
        store.accept(first, signature(duplicate), duplicate);
        assertEquals(720, ExternalApiCallbackStore.path(store.result(first), "body.report.score"));
        store.close(first);
        assertNull(store.result(first));
        assertThrows(IllegalArgumentException.class, () -> store.accept(first, signature(body), body));
    }

    @Test
    public void pendingNotificationDoesNotCompleteTheWait() throws Exception {
        ExternalApiCallbackStore store = store();
        String id = store.register(protocol());
        byte[] body = "{\"job\":\"t1\",\"status\":\"RUNNING\"}".getBytes(StandardCharsets.UTF_8);
        store.accept(id, signature(body), body);
        assertNull(store.result(id));
        assertFalse(ExternalApiCallbackStore.validSignature("test-secret", body, "bad-hex"));
    }

    private static class MemoryRedis extends StringRedisTemplate {
        private final Map<String, String> values = new ConcurrentHashMap<>();
        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(ValueOperations.class.getClassLoader(),
                    new Class[]{ValueOperations.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "get" -> values.get(args[0]);
                        case "set" -> { values.put((String) args[0], (String) args[1]); yield null; }
                        case "setIfAbsent" -> values.putIfAbsent((String) args[0], (String) args[1]) == null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
        @Override
        public Long getExpire(String key, TimeUnit unit) { return values.containsKey(key) ? 30000L : -2L; }
        @Override
        public Long delete(Collection<String> keys) {
            long deleted = 0;
            for (String key : keys) if (values.remove(key) != null) deleted++;
            return deleted;
        }
    }
}
