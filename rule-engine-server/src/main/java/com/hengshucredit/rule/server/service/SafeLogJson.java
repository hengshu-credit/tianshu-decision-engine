package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;

/** 日志序列化失败不能覆盖原执行结果。 */
final class SafeLogJson {
    private SafeLogJson() { }

    static String toJsonSafely(Object value) {
        if (value == null) return null;
        try {
            return JSON.toJSONString(value);
        } catch (StackOverflowError e) {
            return "{\"error\":\"JSON_SERIALIZE_STACK_OVERFLOW\"}";
        } catch (Exception e) {
            return "{\"error\":\"JSON_SERIALIZE_FAILED\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
