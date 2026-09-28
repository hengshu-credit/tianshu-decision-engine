package com.hengshucredit.rule.server.openapi;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.server.service.IdempotencyKeyCalculator;

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
        validateIdempotency(contract.getIdempotency());
        if (contract.isEnabled()) {
            validateRequestMappings(contract.getRequestMappings());
            validateResponseMappings(contract.getResponseMappings());
            new OpenResponseRenderer().validate(contract);
        }
        return JSON.toJSONString(contract);
    }

    public static void validateIdempotency(OpenApiContract.IdempotencyConfig config) {
        if (config == null || !config.isEnabled()) return;
        if (config.getTtlSeconds() < 60 || config.getTtlSeconds() > 7 * 24 * 3600) {
            throw new IllegalArgumentException("幂等键保留时间必须在60秒到7天之间");
        }
        List<com.alibaba.fastjson.JSONObject> operands = idempotencyOperands(config);
        if (operands.isEmpty()) throw new IllegalArgumentException("幂等键必须配置有效的Operand表达式");
        for (int i = 0; i < operands.size(); i++) {
            validateIdempotencyOperand(operands.get(i), operands.size() == 1 ? "$" : "$.components[" + i + "]");
        }
        // 统一服务端运行时计算器的纯函数、操作符和“至少一个稳定字段”规则，
        // 这里暂不校验字段是否存在；字段存在性在发布阶段结合当前定义校验。
        IdempotencyKeyCalculator.validate((com.alibaba.fastjson.JSONObject) JSON.toJSON(config), null);
    }

    private static void validateIdempotencyOperand(com.alibaba.fastjson.JSONObject operand, String path) {
        if (operand == null) throw new IllegalArgumentException("幂等键表达式不能为空: " + path);
        String kind = trim(operand.getString("kind")).toUpperCase(Locale.ROOT);
        if (kind.isEmpty()) throw new IllegalArgumentException("幂等键表达式缺少节点类型: " + path);
        if ("LIST_QUERY".equals(kind)) throw new IllegalArgumentException("幂等键不能使用名单查询节点");
        if ("PATH".equals(kind)) throw new IllegalArgumentException("幂等键字段引用必须携带稳定的refType和refId，不能使用未绑定的PATH引用: " + path);
        if (("PATH".equals(kind) || "REFERENCE".equals(kind))
                && (operand.getLong("refId") == null || trim(operand.getString("refType")).isEmpty())) {
            throw new IllegalArgumentException("幂等键字段引用必须携带稳定的refType和refId: " + path);
        }
        if ("FUNCTION".equals(kind) && operand.getLong("functionId") != null) {
            throw new IllegalArgumentException("幂等键不能调用项目自定义函数");
        }
        if (!("LITERAL".equals(kind) || "PATH".equals(kind) || "REFERENCE".equals(kind)
                || "FUNCTION".equals(kind) || "OPERATION".equals(kind) || "ACCESS".equals(kind)
                || "CAST".equals(kind) || "ARRAY".equals(kind))) {
            throw new IllegalArgumentException("幂等键不支持表达式节点: " + kind);
        }
        if ("FUNCTION".equals(kind)) {
            com.alibaba.fastjson.JSONArray args = operand.getJSONArray("args");
            if (args != null) for (int i = 0; i < args.size(); i++)
                validateIdempotencyOperand(objectAt(args.get(i), path + ".args[" + i + "]"), path + ".args[" + i + "]");
        } else if ("OPERATION".equals(kind)) {
            com.alibaba.fastjson.JSONArray terms = operand.getJSONArray("terms");
            if (terms == null || terms.size() < 2) throw new IllegalArgumentException("幂等键运算至少需要两个运算项");
            for (int i = 0; i < terms.size(); i++) {
                com.alibaba.fastjson.JSONObject term = objectAt(terms.get(i), path + ".terms[" + i + "]");
                validateIdempotencyOperand(term == null ? null : term.getJSONObject("operand"),
                        path + ".terms[" + i + "].operand");
            }
        } else if ("ACCESS".equals(kind)) {
            validateIdempotencyOperand(operand.getJSONObject("target"), path + ".target");
            validateIdempotencyOperand(operand.getJSONObject("accessor"), path + ".accessor");
        } else if ("CAST".equals(kind)) {
            validateIdempotencyOperand(operand.getJSONObject("operand"), path + ".operand");
        } else if ("ARRAY".equals(kind)) {
            com.alibaba.fastjson.JSONArray items = operand.getJSONArray("items");
            if (items == null || items.isEmpty()) throw new IllegalArgumentException("幂等键数组不能为空");
            for (int i = 0; i < items.size(); i++)
                validateIdempotencyOperand(objectAt(items.get(i), path + ".items[" + i + "]"), path + ".items[" + i + "]");
        }
    }

    private static com.alibaba.fastjson.JSONObject objectAt(Object value, String path) {
        Object converted = value instanceof com.alibaba.fastjson.JSONObject ? value : JSON.toJSON(value);
        if (!(converted instanceof com.alibaba.fastjson.JSONObject)) {
            throw new IllegalArgumentException("幂等键节点必须是对象: " + path);
        }
        return (com.alibaba.fastjson.JSONObject) converted;
    }

    public static void validateIdempotencyReferences(OpenApiContract contract, Set<String> availableReferences) {
        if (contract == null || contract.getIdempotency() == null || !contract.getIdempotency().isEnabled()) return;
        Set<String> refs = availableReferences == null ? java.util.Collections.emptySet() : availableReferences;
        for (com.alibaba.fastjson.JSONObject operand : idempotencyOperands(contract.getIdempotency())) {
            validateIdempotencyReferences(operand, refs);
        }
    }

    /** 返回幂等表达式中所有稳定引用，供发布期校验请求映射覆盖范围。 */
    public static Set<String> idempotencyReferences(OpenApiContract contract) {
        Set<String> result = new HashSet<>();
        if (contract == null || contract.getIdempotency() == null || !contract.getIdempotency().isEnabled()) {
            return result;
        }
        for (com.alibaba.fastjson.JSONObject operand : idempotencyOperands(contract.getIdempotency())) {
            collectIdempotencyReferences(operand, result);
        }
        return result;
    }

    private static void collectIdempotencyReferences(Object value, Set<String> result) {
        if (!(value instanceof com.alibaba.fastjson.JSONObject)) return;
        com.alibaba.fastjson.JSONObject operand = (com.alibaba.fastjson.JSONObject) value;
        String kind = trim(operand.getString("kind")).toUpperCase(Locale.ROOT);
        if ("REFERENCE".equals(kind) && operand.getLong("refId") != null
                && !trim(operand.getString("refType")).isEmpty()) {
            result.add(trim(operand.getString("refType")).toUpperCase(Locale.ROOT)
                    + ":" + operand.getLong("refId"));
        }
        if ("FUNCTION".equals(kind)) {
            com.alibaba.fastjson.JSONArray args = operand.getJSONArray("args");
            if (args != null) for (Object arg : args) collectIdempotencyReferences(arg, result);
        } else if ("OPERATION".equals(kind)) {
            com.alibaba.fastjson.JSONArray terms = operand.getJSONArray("terms");
            if (terms != null) for (Object term : terms) {
                if (term instanceof com.alibaba.fastjson.JSONObject) {
                    collectIdempotencyReferences(((com.alibaba.fastjson.JSONObject) term).get("operand"), result);
                }
            }
        } else if ("ACCESS".equals(kind)) {
            collectIdempotencyReferences(operand.get("target"), result);
            collectIdempotencyReferences(operand.get("accessor"), result);
        } else if ("CAST".equals(kind)) {
            collectIdempotencyReferences(operand.get("operand"), result);
        } else if ("ARRAY".equals(kind)) {
            com.alibaba.fastjson.JSONArray items = operand.getJSONArray("items");
            if (items != null) for (Object item : items) collectIdempotencyReferences(item, result);
        }
    }

    private static List<com.alibaba.fastjson.JSONObject> idempotencyOperands(OpenApiContract.IdempotencyConfig config) {
        List<com.alibaba.fastjson.JSONObject> result = new java.util.ArrayList<>();
        if (config == null) return result;
        if (config.getComponents() != null && !config.getComponents().isEmpty()) {
            for (Object component : config.getComponents()) {
                Object converted = component instanceof com.alibaba.fastjson.JSONObject
                        ? component : JSON.toJSON(component);
                if (!(converted instanceof com.alibaba.fastjson.JSONObject)) {
                    throw new IllegalArgumentException("幂等键组件必须是对象");
                }
                com.alibaba.fastjson.JSONObject json = (com.alibaba.fastjson.JSONObject) converted;
                Object operand = json.get("operand");
                Object convertedOperand = operand == null ? json : JSON.toJSON(operand);
                if (!(convertedOperand instanceof com.alibaba.fastjson.JSONObject)) {
                    throw new IllegalArgumentException("幂等键组件 Operand 必须是对象");
                }
                result.add((com.alibaba.fastjson.JSONObject) convertedOperand);
            }
        } else if (config.getOperand() != null) {
            result.add(config.getOperand() instanceof com.alibaba.fastjson.JSONObject
                    ? (com.alibaba.fastjson.JSONObject) config.getOperand()
                    : (com.alibaba.fastjson.JSONObject) JSON.toJSON(config.getOperand()));
        }
        return result;
    }

    private static void validateIdempotencyReferences(Object value, Set<String> available) {
        if (!(value instanceof com.alibaba.fastjson.JSONObject)) return;
        com.alibaba.fastjson.JSONObject operand = (com.alibaba.fastjson.JSONObject) value;
        String kind = trim(operand.getString("kind")).toUpperCase(Locale.ROOT);
        if (("PATH".equals(kind) || "REFERENCE".equals(kind))
                && operand.getLong("refId") != null && !trim(operand.getString("refType")).isEmpty()) {
            String ref = trim(operand.getString("refType")).toUpperCase(Locale.ROOT) + ":" + operand.getLong("refId");
            if (!available.contains(ref)) throw new IllegalArgumentException("幂等键引用的字段 ID 不存在或已停用: " + ref);
        }
        if ("FUNCTION".equals(kind)) {
            com.alibaba.fastjson.JSONArray args = operand.getJSONArray("args");
            if (args != null) for (int i = 0; i < args.size(); i++) validateIdempotencyReferences(args.getJSONObject(i), available);
        } else if ("OPERATION".equals(kind)) {
            com.alibaba.fastjson.JSONArray terms = operand.getJSONArray("terms");
            if (terms != null) for (int i = 0; i < terms.size(); i++) {
                com.alibaba.fastjson.JSONObject term = terms.getJSONObject(i);
                validateIdempotencyReferences(term == null ? null : term.getJSONObject("operand"), available);
            }
        } else if ("ACCESS".equals(kind)) {
            validateIdempotencyReferences(operand.getJSONObject("target"), available);
            validateIdempotencyReferences(operand.getJSONObject("accessor"), available);
        } else if ("CAST".equals(kind)) {
            validateIdempotencyReferences(operand.getJSONObject("operand"), available);
        } else if ("ARRAY".equals(kind)) {
            com.alibaba.fastjson.JSONArray items = operand.getJSONArray("items");
            if (items != null) for (int i = 0; i < items.size(); i++) validateIdempotencyReferences(items.getJSONObject(i), available);
        }
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
