package com.hengshucredit.rule.server.openapi;

import com.alibaba.fastjson.JSON;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 开放接口配置的 JSON 编解码与发布前基础校验。 */
public final class OpenApiContractCodec {

    private static final Pattern REFERENCE_TYPE = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");
    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final Pattern RESPONSE_FIELD = Pattern.compile("^[A-Za-z_][A-Za-z0-9_-]{0,127}$");
    private static final Set<String> TARGET_TYPES = new HashSet<>(Arrays.asList(
            "", "OBJECT", "STRING", "DATE", "DATETIME", "NUMBER", "DECIMAL",
            "INTEGER", "INT", "LONG", "DOUBLE", "BOOLEAN", "BOOL", "LIST", "ARRAY", "MAP"));

    private OpenApiContractCodec() {
    }

    public static OpenApiContract parse(String json) {
        if (json == null || json.trim().isEmpty()) return new OpenApiContract();
        try {
            OpenApiContract contract = JSON.parseObject(json, OpenApiContract.class);
            if (contract == null) throw new IllegalArgumentException("开放接口配置不能为空");
            return contract;
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException
                    && e.getMessage() != null && e.getMessage().contains("开放接口配置")) {
                throw e;
            }
            throw new IllegalArgumentException("开放接口配置不是合法 JSON: " + e.getMessage(), e);
        }
    }

    public static String validateAndNormalize(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        if (json.length() > 1024 * 1024) {
            throw new IllegalArgumentException("开放接口配置不能超过1MB");
        }
        OpenApiContract contract = parse(json);
        if (contract.isEnabled()) {
            validateRequestMappings(contract.getRequestMappings());
            validateResponseMappings(contract.getResponseMappings());
            new OpenResponseRenderer().validate(contract);
        }
        return JSON.toJSONString(contract);
    }

    public static void validateRequestReferences(OpenApiContract contract, Set<String> availableReferences) {
        if (contract == null || !contract.isEnabled()) return;
        Set<String> available = availableReferences == null
                ? java.util.Collections.<String>emptySet() : availableReferences;
        for (OpenApiContract.RequestMapping mapping : contract.getRequestMappings()) {
            String reference = OpenRequestMapper.referenceKey(
                    mapping.getTargetRefType(), mapping.getTargetVarId());
            if (!available.contains(reference)) {
                throw new IllegalArgumentException("请求映射引用的字段 ID 不存在或已停用: " + reference);
            }
        }
    }

    public static void validateResponseReferences(OpenApiContract contract, Set<String> availableReferences) {
        if (contract == null || !contract.isEnabled()) return;
        Set<String> available = availableReferences == null
                ? java.util.Collections.<String>emptySet() : availableReferences;
        for (OpenApiContract.ResponseMapping mapping : contract.getResponseMappings()) {
            String reference = OpenRequestMapper.referenceKey(
                    mapping.getSourceRefType(), mapping.getSourceVarId());
            if (!available.contains(reference)) {
                throw new IllegalArgumentException("响应映射引用的字段 ID 不存在或已停用: " + reference);
            }
        }
    }

    private static void validateRequestMappings(List<OpenApiContract.RequestMapping> mappings) {
        if (mappings == null) return;
        if (mappings.size() > 512) throw new IllegalArgumentException("请求映射最多配置512项");
        Set<String> targets = new HashSet<>();
        for (OpenApiContract.RequestMapping mapping : mappings) {
            if (mapping == null || mapping.getTargetVarId() == null || mapping.getTargetVarId() <= 0) {
                throw new IllegalArgumentException("请求映射目标字段ID不能为空");
            }
            String refType = trim(mapping.getTargetRefType()).toUpperCase(Locale.ROOT);
            if (!REFERENCE_TYPE.matcher(refType).matches()) {
                throw new IllegalArgumentException("请求映射目标引用类型不合法: " + mapping.getTargetRefType());
            }
            String target = OpenRequestMapper.referenceKey(refType, mapping.getTargetVarId());
            if (!targets.add(target)) throw new IllegalArgumentException("请求映射目标字段重复: " + target);
            String sourceType = trim(mapping.getSourceType()).toUpperCase(Locale.ROOT);
            String sourcePath = trim(mapping.getSourcePath());
            if ("BODY".equals(sourceType)) {
                if (sourcePath.length() > 512) throw new IllegalArgumentException("请求JSONPath不能超过512字符");
                RestrictedJsonPath.read(java.util.Collections.emptyMap(), sourcePath);
            } else if ("HEADER".equals(sourceType)) {
                if (sourcePath.length() > 256 || !HEADER_NAME.matcher(sourcePath).matches()) {
                    throw new IllegalArgumentException("请求Header名称不合法: " + mapping.getSourcePath());
                }
            } else {
                throw new IllegalArgumentException("请求映射来源只支持BODY或HEADER: " + mapping.getSourceType());
            }
            String targetType = trim(mapping.getTargetType()).toUpperCase(Locale.ROOT);
            if (!TARGET_TYPES.contains(targetType)) {
                throw new IllegalArgumentException("请求映射目标类型不支持: " + mapping.getTargetType());
            }
            if (mapping.getDefaultValue() != null && mapping.getDefaultValue().length() > 8192) {
                throw new IllegalArgumentException("请求映射默认值不能超过8192字符");
            }
        }
    }

    private static void validateResponseMappings(List<OpenApiContract.ResponseMapping> mappings) {
        if (mappings == null) return;
        if (mappings.size() > 512) throw new IllegalArgumentException("响应映射最多配置512项");
        Set<String> targets = new HashSet<>();
        for (OpenApiContract.ResponseMapping mapping : mappings) {
            if (mapping == null || mapping.getSourceVarId() == null || mapping.getSourceVarId() <= 0) {
                throw new IllegalArgumentException("响应映射来源字段ID不能为空");
            }
            String refType = trim(mapping.getSourceRefType()).toUpperCase(Locale.ROOT);
            if (!REFERENCE_TYPE.matcher(refType).matches()) {
                throw new IllegalArgumentException("响应映射来源引用类型不合法: " + mapping.getSourceRefType());
            }
            String targetField = trim(mapping.getTargetField());
            if (!RESPONSE_FIELD.matcher(targetField).matches()) {
                throw new IllegalArgumentException("响应映射对外字段名不合法: " + mapping.getTargetField());
            }
            if (!targets.add(targetField)) {
                throw new IllegalArgumentException("响应映射对外字段重复: " + targetField);
            }
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
