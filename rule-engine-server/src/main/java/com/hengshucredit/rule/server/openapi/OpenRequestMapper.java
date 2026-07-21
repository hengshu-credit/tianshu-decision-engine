package com.hengshucredit.rule.server.openapi;

import com.alibaba.fastjson.JSON;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 将对外请求按稳定变量 ID 映射为当前规则脚本输入。 */
@Component
public class OpenRequestMapper {

    public Map<String, Object> map(OpenApiContract contract, Object body, Map<String, String> headers,
                                   Map<String, String> targetNamesByReference) {
        if (contract == null) throw new IllegalArgumentException("开放接口配置不能为空");
        Map<String, String> targetNames = targetNamesByReference == null
                ? Collections.<String, String>emptyMap() : targetNamesByReference;
        Map<String, String> normalizedHeaders = normalizeHeaders(headers);
        Map<String, Object> result = new LinkedHashMap<>();
        List<OpenApiContract.RequestMapping> mappings = contract.getRequestMappings();
        for (OpenApiContract.RequestMapping mapping : mappings) {
            Long varId = mapping == null ? null : mapping.getTargetVarId();
            String refType = mapping == null ? null : mapping.getTargetRefType();
            String referenceKey = referenceKey(refType, varId);
            String targetName = referenceKey == null ? null : targetNames.get(referenceKey);
            if (targetName == null || targetName.trim().isEmpty()) {
                throw new IllegalArgumentException("请求映射引用的字段 ID 不存在或已停用: "
                        + refType + ":" + varId);
            }
            Object value = sourceValue(mapping, body, normalizedHeaders);
            if (missing(value) && mapping.getDefaultValue() != null) value = mapping.getDefaultValue();
            if (missing(value) && mapping.isRequired()) {
                throw new IllegalArgumentException("请求必填字段缺失: " + mapping.getSourcePath());
            }
            result.put(targetName, convert(value, mapping.getTargetType(), mapping.getSourcePath()));
        }
        return result;
    }

    public static String referenceKey(String refType, Long varId) {
        if (refType == null || refType.trim().isEmpty() || varId == null) return null;
        return refType.trim().toUpperCase(Locale.ROOT) + ":" + varId;
    }

    private Object sourceValue(OpenApiContract.RequestMapping mapping, Object body,
                               Map<String, String> normalizedHeaders) {
        String sourceType = text(mapping.getSourceType()).toUpperCase(Locale.ROOT);
        String sourcePath = text(mapping.getSourcePath());
        if ("BODY".equals(sourceType)) return RestrictedJsonPath.read(body, sourcePath);
        if ("HEADER".equals(sourceType)) return normalizedHeaders.get(sourcePath.toLowerCase(Locale.ROOT));
        throw new IllegalArgumentException("不支持的请求映射来源: " + mapping.getSourceType());
    }

    private Map<String, String> normalizeHeaders(Map<String, String> headers) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (headers == null) return normalized;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null) normalized.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return normalized;
    }

    private Object convert(Object value, String targetType, String sourcePath) {
        if (value == null) return null;
        String type = targetType == null ? "" : targetType.trim().toUpperCase(Locale.ROOT);
        if (type.isEmpty() || "OBJECT".equals(type)) return value;
        try {
            if ("STRING".equals(type) || "DATE".equals(type) || "DATETIME".equals(type)) {
                return value instanceof String ? value : String.valueOf(value);
            }
            if ("NUMBER".equals(type) || "DECIMAL".equals(type)) return decimal(value);
            if ("INTEGER".equals(type) || "INT".equals(type)) return decimal(value).intValueExact();
            if ("LONG".equals(type)) return decimal(value).longValueExact();
            if ("DOUBLE".equals(type)) return decimal(value).doubleValue();
            if ("BOOLEAN".equals(type) || "BOOL".equals(type)) return bool(value);
            if ("LIST".equals(type) || "ARRAY".equals(type)) {
                if (value instanceof List) return value;
                return JSON.parseArray(String.valueOf(value));
            }
            if ("MAP".equals(type)) {
                if (value instanceof Map) return value;
                return JSON.parseObject(String.valueOf(value));
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("请求字段类型转换失败: " + sourcePath + " -> " + targetType, e);
        }
        throw new IllegalArgumentException("不支持的请求字段目标类型: " + targetType);
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        return new BigDecimal(String.valueOf(value));
    }

    private Boolean bool(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) return Boolean.FALSE;
        throw new IllegalArgumentException("布尔值必须为 true/false/1/0");
    }

    private boolean missing(Object value) {
        return value == null || (value instanceof String && ((String) value).trim().isEmpty());
    }

    private String text(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("请求映射来源不能为空");
        return value.trim();
    }
}
