package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 外数调用报文留存策略。策略只处理诊断/分析副本，不改变规则实际执行对象。
 */
final class ExternalApiPayloadCapturePolicy {
    private static final int MAX_FIELD_BYTES = 50 * 1024 * 1024;
    private static final String WILDCARD = "[*]";

    private ExternalApiPayloadCapturePolicy() { }

    static void validate(String configJson) {
        parseConfig(configJson);
    }

    static String originalBody(String configJson, String phase, String originalBody, Capture capture) {
        if (originalBody == null) return null;
        JSONObject config = parseConfig(configJson);
        JSONObject phaseConfig = phaseConfig(config, phase);
        Object configured = phaseConfig.get("saveOriginal");
        boolean saveOriginal = configured == null || Boolean.TRUE.equals(configured);
        if (saveOriginal) return originalBody;
        if (capture != null && "FAILED".equals(String.valueOf(capture.metadata.get("status")))) {
            capture.metadata.put("originalStored", true);
            capture.metadata.put("originalStoredReason", "CAPTURE_FAILED");
            return originalBody;
        }
        return null;
    }

    static Capture capture(String configJson, String phase, String originalBody,
                           Object processedBody, Map<String, Object> variables) {
        try {
            JSONObject config = parseConfig(configJson);
            JSONObject phaseConfig = phaseConfig(config, phase);
            String source = text(phaseConfig.get("source"), "ORIGINAL").toUpperCase(java.util.Locale.ROOT);
            if (!"ORIGINAL".equals(source) && !"PROCESSED".equals(source)) {
                throw new IllegalArgumentException("报文留存 source 只能是 ORIGINAL 或 PROCESSED");
            }
            Object selected;
            if ("ORIGINAL".equals(source)) {
                if (originalBody == null) return Capture.unavailable(source);
                selected = originalBody;
            } else {
                if (processedBody == null) return Capture.unavailable(source);
                selected = deepCopy(processedBody);
            }

            Set<String> omitted = new LinkedHashSet<>();
            JSONObject decrypt = phaseConfig.getJSONObject("decrypt");
            if (decrypt != null && Boolean.TRUE.equals(decrypt.getBoolean("enabled"))) {
                String path = text(decrypt.get("path"), "$");
                String mode = text(decrypt.get("mode"), "BASE64").toUpperCase(java.util.Locale.ROOT);
                String key = null;
                if ("TRIPLE_DES_BASE64".equals(mode)) {
                    String keyVariable = text(decrypt.get("keyVariable"), "");
                    if (keyVariable.isBlank() || variables == null || variables.get(keyVariable) == null) {
                        throw new IllegalArgumentException("3DES报文解密必须配置有效 keyVariable");
                    }
                    key = String.valueOf(variables.get(keyVariable));
                }
                if (!"$".equals(path) && selected instanceof String) {
                    Object parsed = parseJson((String) selected);
                    if (parsed == null) throw new IllegalArgumentException("报文解密路径要求报文是 JSON");
                    selected = parsed;
                }
                selected = decryptAtPath(selected, path, mode, key);
                source = source + "+DECRYPTED";
            }

            Object structured = selected;
            if (selected instanceof String) {
                String text = (String) selected;
                Object parsed = parseJson(text);
                if (parsed != null && (!"$".equals(pathOf(phaseConfig, "decrypt")) || hasFilters(phaseConfig))) {
                    structured = parsed;
                } else if (hasFilters(phaseConfig)) {
                    throw new IllegalArgumentException("配置了字段排除，但报文不是 JSON 对象或数组");
                }
            }
            List<String> excludePaths = stringList(phaseConfig.get("excludePaths"));
            for (String path : excludePaths) {
                List<Object> tokens = parsePath(path);
                if (tokens.isEmpty()) throw new IllegalArgumentException("报文排除路径无效: " + path);
                removeAtPath(structured, tokens, 0, path, omitted);
            }
            int maxFieldBytes = integer(phaseConfig.get("maxFieldBytes"), 0);
            if (maxFieldBytes < 0 || maxFieldBytes > MAX_FIELD_BYTES) {
                throw new IllegalArgumentException("maxFieldBytes 必须在0到" + MAX_FIELD_BYTES + "之间");
            }
            if (maxFieldBytes > 0) trimLargeFields(structured, maxFieldBytes, "$", omitted);

            String captured = structured instanceof String && excludePaths.isEmpty() && maxFieldBytes == 0
                    ? (String) structured : JSON.toJSONString(structured);
            if (captured == null) return Capture.unavailable(source);
            return new Capture(captured, metadata(omitted, source,
                    omitted.isEmpty() ? "CAPTURED" : "FILTERED"));
        } catch (RuntimeException error) {
            return Capture.failed(error.getMessage());
        }
    }

    static Capture capture(String configJson, String phase, String originalBody,
                           Object processedBody) {
        return capture(configJson, phase, originalBody, processedBody, Collections.emptyMap());
    }

    private static JSONObject parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) return new JSONObject();
        try {
            JSONObject config = JSON.parseObject(configJson);
            if (config == null) throw new IllegalArgumentException("报文留存配置不能为空对象");
            phaseConfig(config, "request");
            phaseConfig(config, "response");
            return config;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("报文留存配置必须是合法 JSON", error);
        }
    }

    private static JSONObject phaseConfig(JSONObject config, String phase) {
        Object value = config.get(phase);
        if (value == null) return new JSONObject();
        if (!(value instanceof Map)) throw new IllegalArgumentException("报文留存 " + phase + " 配置必须是对象");
        JSONObject result;
        if (value instanceof JSONObject jsonObject) {
            result = jsonObject;
        } else {
            result = new JSONObject();
            if (value instanceof Map<?, ?> raw) {
                raw.forEach((key, item) -> {
                    if (key != null) result.put(String.valueOf(key), item);
                });
            }
        }
        if (result.get("saveOriginal") != null && !(result.get("saveOriginal") instanceof Boolean)) {
            throw new IllegalArgumentException("报文留存 " + phase + " saveOriginal 必须是布尔值");
        }
        Object source = result.get("source");
        if (source != null && !Set.of("ORIGINAL", "PROCESSED").contains(String.valueOf(source).trim().toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("报文留存 " + phase + " source 只能是 ORIGINAL 或 PROCESSED");
        }
        stringList(result.get("excludePaths"));
        integer(result.get("maxFieldBytes"), 0);
        JSONObject decrypt = result.getJSONObject("decrypt");
        if (decrypt != null) {
            String mode = text(decrypt.get("mode"), "BASE64").toUpperCase(java.util.Locale.ROOT);
            String path = text(decrypt.get("path"), "$");
            if (!Set.of("BASE64", "TRIPLE_DES_BASE64").contains(mode)) {
                throw new IllegalArgumentException("报文留存 " + phase + " 解密模式不受支持");
            }
            if (parsePath(path).isEmpty()) {
                throw new IllegalArgumentException("报文留存 " + phase + " 解密路径无效: " + path);
            }
            if (Boolean.TRUE.equals(decrypt.getBoolean("enabled"))
                    && "TRIPLE_DES_BASE64".equals(mode)
                    && text(decrypt.get("keyVariable"), "").isBlank()) {
                throw new IllegalArgumentException("报文留存 " + phase + " 3DES解密必须配置 keyVariable");
            }
        }
        return result;
    }

    private static boolean hasFilters(JSONObject config) {
        return !stringList(config.get("excludePaths")).isEmpty() || integer(config.get("maxFieldBytes"), 0) > 0;
    }

    private static String pathOf(JSONObject config, String key) {
        JSONObject decrypt = config.getJSONObject(key);
        return decrypt == null ? "$" : text(decrypt.get("path"), "$");
    }

    private static Map<String, Object> metadata(Set<String> omitted, String source, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("source", source);
        result.put("omittedPaths", new ArrayList<>(omitted));
        return result;
    }

    private static Object decryptAtPath(Object root, String path, String mode, String key) {
        List<Object> tokens = parsePath(path);
        if (tokens.isEmpty()) throw new IllegalArgumentException("报文解密路径无效: " + path);
        if (tokens.size() == 1 && "$".equals(tokens.get(0))) return decryptValue(root, mode, key);
        Object target = readAtPath(root, tokens, 0);
        if (!(target instanceof String)) throw new IllegalArgumentException("报文解密路径必须指向字符串: " + path);
        Object decrypted = decryptValue(target, mode, key);
        if (!replaceAtPath(root, tokens, 0, decrypted)) throw new IllegalArgumentException("报文解密路径不存在: " + path);
        return root;
    }

    private static Object decryptValue(Object value, String mode, String key) {
        if (!(value instanceof String)) throw new IllegalArgumentException("报文解密内容必须是字符串");
        try {
            byte[] encrypted = Base64.getDecoder().decode((String) value);
            byte[] decrypted;
            if ("BASE64".equals(mode)) {
                decrypted = encrypted;
            } else if ("TRIPLE_DES_BASE64".equals(mode)) {
                byte[] keyBytes = Base64.getDecoder().decode(key);
                if (keyBytes.length != 24) throw new IllegalArgumentException("3DES密钥必须是24字节 Base64");
                Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "DESede"));
                decrypted = cipher.doFinal(encrypted);
            } else {
                throw new IllegalArgumentException("不支持的报文解密模式: " + mode);
            }
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("报文解密失败，请检查密文或密钥", error);
        }
    }

    private static Object parseJson(String value) {
        try { return JSON.parse(value); } catch (RuntimeException ignored) { return null; }
    }

    private static Object deepCopy(Object value) {
        if (value == null) return null;
        if (value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        return JSON.parse(JSON.toJSONString(value));
    }

    private static List<String> stringList(Object value) {
        if (value == null) return Collections.emptyList();
        if (!(value instanceof JSONArray) && !(value instanceof List)) {
            throw new IllegalArgumentException("excludePaths 必须是字符串数组");
        }
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof String) || ((String) item).isBlank()) throw new IllegalArgumentException("excludePaths 必须只包含非空字符串");
            if (parsePath((String) item).isEmpty()) throw new IllegalArgumentException("报文排除路径无效: " + item);
            result.add((String) item);
        }
        return result;
    }

    private static int integer(Object value, int fallback) {
        if (value == null) return fallback;
        try { return new java.math.BigDecimal(String.valueOf(value)).intValueExact(); }
        catch (RuntimeException error) { throw new IllegalArgumentException("maxFieldBytes 必须是整数", error); }
    }

    private static List<Object> parsePath(String value) {
        if (value == null || value.isBlank() || !value.startsWith("$")) return Collections.emptyList();
        List<Object> tokens = new ArrayList<>();
        tokens.add("$");
        int index = 1;
        while (index < value.length()) {
            char current = value.charAt(index);
            if (current == '.') {
                int end = index + 1;
                while (end < value.length() && value.charAt(end) != '.' && value.charAt(end) != '[') end++;
                if (end == index + 1) return Collections.emptyList();
                tokens.add(value.substring(index + 1, end));
                index = end;
            } else if (current == '[') {
                int end = value.indexOf(']', index);
                if (end < 0) return Collections.emptyList();
                String token = value.substring(index + 1, end);
                if ("*".equals(token)) tokens.add(WILDCARD);
                else if (token.matches("\\d+")) tokens.add(Integer.valueOf(token));
                else if (token.length() >= 2 && token.charAt(0) == '\'' && token.charAt(token.length() - 1) == '\'') tokens.add(token.substring(1, token.length() - 1));
                else return Collections.emptyList();
                index = end + 1;
            } else return Collections.emptyList();
        }
        return tokens;
    }

    private static void removeAtPath(Object current, List<Object> tokens, int index,
                                     String originalPath, Set<String> omitted) {
        if (index == tokens.size() - 1) return;
        Object token = tokens.get(index + 1);
        if (token instanceof String && WILDCARD.equals(token)) {
            if (current instanceof List) {
                List<?> values = (List<?>) current;
                for (int i = values.size() - 1; i >= 0; i--) {
                    removeAtPath(values.get(i), tokens, index + 1, originalPath, omitted);
                }
            }
            return;
        }
        if (index + 1 == tokens.size() - 1) {
            if (current instanceof Map && token instanceof String) {
                if (((Map<?, ?>) current).remove(token) != null) omitted.add(originalPath);
            } else if (current instanceof List && token instanceof Integer) {
                int position = (Integer) token;
                if (position >= 0 && position < ((List<?>) current).size()) { ((List<?>) current).remove(position); omitted.add(originalPath); }
            }
            return;
        }
        Object next = readChild(current, token);
        if (next != null) removeAtPath(next, tokens, index + 1, originalPath, omitted);
    }

    private static void trimLargeFields(Object current, int maxBytes, String path, Set<String> omitted) {
        if (current instanceof Map<?, ?> map) {
            for (Object key : new ArrayList<>(map.keySet())) {
                Object value = map.get(key);
                String childPath = path + "." + key;
                if (JSON.toJSONString(value).getBytes(StandardCharsets.UTF_8).length > maxBytes) { map.remove(key); omitted.add(childPath); }
                else trimLargeFields(value, maxBytes, childPath, omitted);
            }
        } else if (current instanceof List<?> list) {
            for (int i = list.size() - 1; i >= 0; i--) {
                String childPath = path + "[" + i + "]";
                if (JSON.toJSONString(list.get(i)).getBytes(StandardCharsets.UTF_8).length > maxBytes) { list.remove(i); omitted.add(childPath); }
                else trimLargeFields(list.get(i), maxBytes, childPath, omitted);
            }
        }
    }

    private static Object readChild(Object current, Object token) {
        if (current instanceof Map && token instanceof String) return ((Map<?, ?>) current).get(token);
        if (current instanceof List && token instanceof Integer) {
            int position = (Integer) token; return position >= 0 && position < ((List<?>) current).size() ? ((List<?>) current).get(position) : null;
        }
        return null;
    }

    private static Object readAtPath(Object root, List<Object> tokens, int index) {
        if (index == tokens.size() - 1) return root;
        Object next = readChild(root, tokens.get(index + 1));
        return next == null ? null : readAtPath(next, tokens, index + 1);
    }

    private static boolean replaceAtPath(Object root, List<Object> tokens, int index, Object value) {
        if (index + 1 == tokens.size() - 1) {
            Object token = tokens.get(index + 1);
            if (root instanceof JSONObject map && token instanceof String) {
                map.put((String) token, value);
                return true;
            }
            if (root instanceof JSONArray list && token instanceof Integer) {
                int position = (Integer) token;
                if (position >= 0 && position < list.size()) {
                    list.set(position, value);
                    return true;
                }
            }
            return false;
        }
        Object next = readChild(root, tokens.get(index + 1));
        return next != null && replaceAtPath(next, tokens, index + 1, value);
    }

    private static String text(Object value, String fallback) { return value == null ? fallback : String.valueOf(value).trim(); }

    static final class Capture {
        private final String capturedBody;
        private final Map<String, Object> metadata;
        private Capture(String capturedBody, Map<String, Object> metadata) { this.capturedBody = capturedBody; this.metadata = metadata; }
        static Capture unavailable(String source) { return new Capture(null, ExternalApiPayloadCapturePolicy.metadata(Collections.<String>emptySet(), source, "UNAVAILABLE")); }
        static Capture failed(String message) { Map<String, Object> m = ExternalApiPayloadCapturePolicy.metadata(Collections.<String>emptySet(), "", "FAILED"); m.put("message", message == null ? "报文留存处理失败" : message); return new Capture(null, m); }
        String capturedBody() { return capturedBody; }
        Map<String, Object> metadata() { return metadata; }
    }
}
