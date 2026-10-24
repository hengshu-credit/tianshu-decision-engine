package com.hengshucredit.rule.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 对外展示映射；多个标准码可以共用对外 code，执行语义始终来自 PlatformCode。 */
public record ExternalCodeMapping(long version, boolean enabled, Map<String, Entry> mappings) {
    public static final String STANDARD_CODE_HEADER = "X-Rule-Standard-Code";
    public static final String VERSION_HEADER = "X-Rule-Code-Mapping-Version";
    public static final ExternalCodeMapping DEFAULT = new ExternalCodeMapping(0, false, Map.of());

    public ExternalCodeMapping {
        if (version < 0) throw new IllegalArgumentException("映射版本不能为负数");
        Map<String, Entry> values = new LinkedHashMap<>();
        if (mappings != null) mappings.forEach((key, entry) -> {
            if (PlatformCode.find(key) == null) throw new IllegalArgumentException("不支持的系统结果码: " + key);
            Objects.requireNonNull(entry, "映射配置不能为空");
            values.put(key, entry);
        });
        mappings = Map.copyOf(values);
    }

    public String code(PlatformCode status) {
        Entry entry = enabled ? mappings.get(status.code()) : null;
        return entry == null || entry.code().isEmpty() ? status.code() : entry.code();
    }

    public String message(PlatformCode status) {
        Entry entry = enabled ? mappings.get(status.code()) : null;
        return entry == null || entry.message().isEmpty() ? status.message() : entry.message();
    }

    public record Entry(String code, String message) {
        public Entry {
            code = code == null ? "" : code;
            message = message == null ? "" : message;
            if (!code.isEmpty() && !code.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}")) {
                throw new IllegalArgumentException("对外 code 仅支持 1–64 位字母、数字、下划线、点、冒号和短横线");
            }
            if (message.length() > 120 || !message.isEmpty() && message.isBlank()) {
                throw new IllegalArgumentException("对外描述不能仅为空白且不能超过 120 字符");
            }
        }
    }

    /** 只接受明确的配置结构；不通过对外 code 反向猜测标准码。 */
    public static ExternalCodeMapping from(long version, Map<String, Object> config) {
        if (config == null) return DEFAULT;
        if (!(config.get("enabled") instanceof Boolean enabled)) throw new IllegalArgumentException("结果码映射启用状态无效");
        if (!(config.get("mappings") instanceof Map<?, ?> entries)) throw new IllegalArgumentException("结果码映射表无效");
        Map<String, Entry> values = new LinkedHashMap<>();
        entries.forEach((key, raw) -> {
            if (!(key instanceof String code) || !(raw instanceof Map<?, ?> value)
                    || value.keySet().stream().anyMatch(field -> !"code".equals(field) && !"message".equals(field))) {
                throw new IllegalArgumentException("结果码映射项无效");
            }
            values.put(code, new Entry(text(value.get("code")), text(value.get("message"))));
        });
        return new ExternalCodeMapping(version, enabled, values);
    }

    private static String text(Object value) {
        if (value == null) return "";
        if (!(value instanceof String text)) throw new IllegalArgumentException("code 和描述必须是字符串");
        return text;
    }
}
