package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;

import java.util.HashSet;
import java.util.Set;

/** 保存、审批、预览和真实调用共用 V2 结构校验。 */
public final class ExternalApiExecutionConfig {
    private static final Set<String> PURE_FUNCTIONS = BuiltinFunctionCatalog.definitions().stream()
            .filter(function -> function.getImplClass() != null && Set.of("com.hengshucredit.rule.core.function.AggregateBuiltinFunctions",
                    "com.hengshucredit.rule.core.function.DecisionBuiltinFunctions", "com.hengshucredit.rule.core.function.DigestBuiltinFunctions").contains(function.getImplClass()))
            .map(com.hengshucredit.rule.model.entity.RuleFunction::getFuncCode).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private ExternalApiExecutionConfig() { }
    static void validateVersionFunction(com.hengshucredit.rule.model.entity.RuleFunction function) {
        if (!PURE_FUNCTIONS.contains(function.getFuncCode()) || !Set.of(
                "com.hengshucredit.rule.core.function.AggregateBuiltinFunctions",
                "com.hengshucredit.rule.core.function.DecisionBuiltinFunctions",
                "com.hengshucredit.rule.core.function.DigestBuiltinFunctions").contains(function.getImplClass() == null ? "" : function.getImplClass()))
            throw new IllegalArgumentException("外数配置仅支持无副作用的内置计算函数版本");
    }
    static void validateFunction(JSONObject operand) {
        if ("FUNCTION".equals(operand.getString("kind")) && !PURE_FUNCTIONS.contains(operand.getString("functionCode"))) {
            throw new IllegalArgumentException("外数配置表达式只支持无副作用的内置计算函数: " + operand.getString("functionCode"));
        }
    }
    public static void bindReferences(Object node, java.util.Map<String, String> paths) {
        if (node instanceof JSONObject value) {
            validateFunction(value);
            String kind = value.getString("kind");
            if ("LITERAL".equals(kind)) return;
            if (com.hengshucredit.rule.core.compiler.OperandVersion.fixedVersionId(value) != null) {
                if ("FUNCTION".equals(kind)) bindReferences(value.get("args"), paths);
                return;
            }
            if ("REFERENCE".equals(kind) || "PATH".equals(kind)) {
                if (value.get("refId") != null && value.get("refType") != null) {
                    String key = value.getString("refType") + ":" + value.getString("refId");
                    if (!paths.containsKey(key)) throw new IllegalArgumentException("外数参数引用不属于可用字段: " + key);
                } else {
                    String path = value.getString("value");
                    boolean protocol = path != null && path.matches("(?:response|request|authentication|steps|callbacks|status|body|headers|rawBody)(?:[.\\[].+)?|input\\.__apiFields\\..+|httpStatus|callId|costTimeMs|errorMessage");
                    if (!protocol) {
                        var matches = paths.entrySet().stream().filter(entry -> java.util.Objects.equals(path, entry.getValue())).toList();
                        if (matches.size() != 1) throw new IllegalArgumentException("字段路径无法唯一反解，请选择字段: " + path);
                        String[] reference = matches.get(0).getKey().split(":", 2);
                        value.put("refType", reference[0]); value.put("refId", Long.valueOf(reference[1])); value.put("resolved", true);
                    }
                }
            }
            value.forEach((key, child) -> { if (!Set.of("sample", "samples").contains(key)) bindReferences(child, paths); });
        } else if (node instanceof Iterable<?> list) list.forEach(child -> bindReferences(child, paths));
    }
    public static void validate(RuleExternalApiConfig api) {
        JSONObject spec = ExternalApiRequestPlan.specification(api);
        if (spec == null) return;
        if (spec.getIntValue("version") != 2) throw new IllegalArgumentException("外数执行配置版本必须为 2");
        validateFields(spec);
        for (String key : new String[]{"requestBranches", "responseBranches", "exceptionBranches", "billingBranches", "retryBranches"}) {
            if (spec.getJSONArray(key) == null) continue;
            Set<String> ids = new HashSet<>();
            for (Object raw : spec.getJSONArray(key)) {
                JSONObject branch = JSON.parseObject(JSON.toJSONString(raw));
                identity(branch, ids, "条件分支");
                validateFields(branch);
                validateConditionIfPresent(branch.get("condition"), "条件分支判断");
                if ("responseBranches".equals(key)) validateResponseBranch(branch, "响应分支");
                if ("retryBranches".equals(key)) {
                    int retries = branch.getIntValue("retryCount");
                    if (retries < 0 || retries > 10) throw new IllegalArgumentException("重试次数必须在 0 到 10 之间");
                }
            }
        }
        if (spec.getJSONArray("steps") == null || spec.getJSONArray("steps").isEmpty()) return;
        if (!"ASYNC".equals(api.getRequestMode())) throw new IllegalArgumentException("多步链路必须选择异步请求模式");
        Set<String> prior = new HashSet<>();
        for (Object raw : spec.getJSONArray("steps")) {
            JSONObject step = JSON.parseObject(JSON.toJSONString(raw));
            Set<String> before = new HashSet<>(prior);
            String id = identity(step, prior, "步骤");
            if (!Set.of("HTTP", "CALLBACK").contains(step.getString("type"))) throw new IllegalArgumentException("步骤类型必须是 HTTP 或 CALLBACK");
            if ("CALLBACK".equals(step.getString("type"))) {
                JSONObject callback = step.getJSONObject("callback");
                if (callback == null || callback.getString("url") == null
                        || !callback.getString("url").matches("https?://.+/api/external-callback/\\$\\{invocationId}")) {
                    throw new IllegalArgumentException("回调步骤需要有效的公网回调地址模板");
                }
                for (String key : new String[]{"signatureHeader", "signatureSecret", "statusPath", "successValue"}) {
                    if (callback.getString(key) == null || callback.getString(key).isBlank()) throw new IllegalArgumentException("回调步骤缺少 " + key);
                }
            } else if (step.getLong("apiConfigId") == null && (step.getString("endpointUrl") == null || step.getString("endpointUrl").isBlank())) {
                throw new IllegalArgumentException("HTTP 步骤需要关联 API 或填写请求地址");
            }
            JSONObject poll = step.getJSONObject("poll");
            validateConditionIfPresent(step.get("when"), "步骤执行条件");
            validateConditionIfPresent(step.get("successConditionTree"), "步骤成功条件");
            validateConditionIfPresent(step.get("exceptionConditionTree"), "步骤异常条件");
            validateConditionIfPresent(step.get("retryConditionTree"), "步骤重试条件");
            validateConditionIfPresent(step.get("exceptionCondition"), "步骤异常条件");
            validateConditionIfPresent(step.get("successCondition"), "步骤成功条件");
            if (step.get("responseBranches") instanceof Iterable<?> responseBranches) {
                Set<String> responseBranchIds = new HashSet<>();
                for (Object branch : responseBranches) {
                    if (branch instanceof java.util.Map<?, ?> map) {
                        JSONObject responseBranch = JSON.parseObject(JSON.toJSONString(map));
                        identity(responseBranch, responseBranchIds, "步骤响应分支");
                        validateConditionIfPresent(responseBranch.get("condition"), "步骤响应分支条件");
                        validateResponseBranch(responseBranch, "步骤响应分支");
                    }
                }
            }
            if (poll != null) {
                ExternalApiConfigValidator.positive(poll, "intervalMs", 1000);
                int maxAttempts = ExternalApiConfigValidator.nonNegative(poll, "maxAttempts", 20);
                if (api.getAsyncTimeoutMs() != null && api.getAsyncTimeoutMs() == 0 && maxAttempts > 0) {
                    throw new IllegalArgumentException("异步等待超时为 0 时，步骤 maxAttempts 必须为 0 才能持续等待");
                }
                if (poll.get("until") == null) throw new IllegalArgumentException("轮询步骤必须配置完成条件");
                validateConditionIfPresent(poll.get("until"), "轮询完成条件");
                validateConditionIfPresent(poll.get("failure"), "轮询失败条件");
                if (poll.get("failureMode") != null && !Set.of("CONTINUE", "TERMINATE")
                        .contains(poll.getString("failureMode").trim().toUpperCase(java.util.Locale.ROOT))) {
                    throw new IllegalArgumentException("步骤失败处理方式只能是 CONTINUE 或 TERMINATE");
                }
                if (poll.get("maxIntervalMs") != null) ExternalApiConfigValidator.positive(poll, "maxIntervalMs", 60000);
                if (poll.get("backoffMultiplier") != null && (poll.getDoubleValue("backoffMultiplier") < 1 || poll.getDoubleValue("backoffMultiplier") > 10)) {
                    throw new IllegalArgumentException("轮询退避倍数必须在 1 到 10 之间");
                }
            }
            validateFields(step);
            validateStepReferences(step, before, id);
        }
    }

    private static void validateConditionIfPresent(Object raw, String label) {
        if (!hasCondition(raw)) return;
        ExternalApiConfigValidator.validateCondition(raw, label);
    }

    private static boolean hasCondition(Object raw) {
        if (raw instanceof String text) {
            if (text.isBlank()) return false;
            try {
                return hasCondition(JSON.parseObject(text));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("条件必须是合法 JSON", error);
            }
        }
        if (!(raw instanceof java.util.Map<?, ?> map) || map.isEmpty()) return false;
        Object children = map.get("children");
        if (children instanceof Iterable<?> values) {
            for (Object child : values) if (hasCondition(child)) return true;
            return false;
        }
        Object path = map.get("path");
        Object field = map.get("field");
        Object varCode = map.get("varCode");
        return (path != null && !String.valueOf(path).isBlank())
                || (field != null && !String.valueOf(field).isBlank())
                || (varCode != null && !String.valueOf(varCode).isBlank())
                || map.get("left") != null
                || map.get("operator") != null;
    }

    private static String identity(JSONObject item, Set<String> ids, String label) {
        String id = item.getString("id");
        if (id == null || !id.matches("[A-Za-z0-9_-]+") || !ids.add(id)) throw new IllegalArgumentException(label + " ID 必须非空且唯一");
        return id;
    }

    private static void validateFields(JSONObject config) {
        Set<String> ids = new HashSet<>();
        Set<String> targets = new HashSet<>();
        for (JSONObject field : ExternalApiRequestPlan.fields(config)) {
            ExternalApiFileInputs.validate(field);
            identity(field, ids, "请求字段");
            if (!Set.of("HEADER", "QUERY", "COOKIE", "JSON", "FORM", "FORM_DATA").contains(field.getString("location"))) throw new IllegalArgumentException("请求字段位置无效");
            if ("HEADER".equals(field.getString("location")) && field.getBooleanValue("overridable")) throw new IllegalArgumentException("Header 字段不能由变量或对象覆盖");
            if (field.getString("path") == null || field.getString("path").isBlank()) throw new IllegalArgumentException("请求字段路径不能为空");
            String targetPath = "HEADER".equals(field.getString("location"))
                    ? field.getString("path").trim().toLowerCase(java.util.Locale.ROOT)
                    : field.getString("path");
            if (!targets.add(field.getString("location") + ":" + targetPath)) throw new IllegalArgumentException("请求字段目标路径重复");
            String policy = field.getString("nullPolicy");
            if (policy != null && !Set.of("DEFAULT", "OMIT", "NULL", "EMPTY").contains(policy)) throw new IllegalArgumentException("传空策略无效");
        }
    }

    private static void validateResponseBranch(JSONObject branch, String label) {
        String mode = branch.getString("mode");
        if (mode != null && !Set.of("FIELDS", "VALUE").contains(mode.trim().toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException(label + "模式只能是 FIELDS 或 VALUE");
        }
        if ("VALUE".equalsIgnoreCase(mode)) {
            if (!branch.containsKey("value")) throw new IllegalArgumentException(label + "整体取值不能为空");
            String outputPath = branch.getString("outputPath");
            if (outputPath != null && outputPath.isBlank()) throw new IllegalArgumentException(label + "整体取值字段路径不能为空");
            return;
        }
        Object rawFields = branch.get("outputFields");
        if (!(rawFields instanceof Iterable<?> fields)) return;
        Set<String> ids = new HashSet<>();
        Set<String> paths = new HashSet<>();
        for (Object raw : fields) {
            JSONObject field = JSON.parseObject(JSON.toJSONString(raw));
            String path = field.getString("path");
            if (path == null || path.isBlank()) throw new IllegalArgumentException(label + "输出字段路径不能为空");
            String id = field.getString("id");
            if (id != null && !id.isBlank() && !ids.add(id)) throw new IllegalArgumentException(label + "输出字段 ID 重复: " + id);
            if (!paths.add(path)) throw new IllegalArgumentException(label + "输出字段路径重复: " + path);
            if (!field.containsKey("value")) throw new IllegalArgumentException(label + "输出字段缺少取值: " + path);
        }
    }

    private static void validateStepReferences(Object value, Set<String> prior, String ownId) {
        if (value instanceof java.util.Map<?, ?> map) map.values().forEach(child -> validateStepReferences(child, prior, ownId));
        else if (value instanceof Iterable<?> list) list.forEach(child -> validateStepReferences(child, prior, ownId));
        else if (value instanceof String text) {
            var matcher = java.util.regex.Pattern.compile("(?:\\$\\.|\\$\\{)?steps\\.([A-Za-z0-9_-]+)\\.").matcher(text);
            while (matcher.find()) if (!prior.contains(matcher.group(1))) throw new IllegalArgumentException("步骤 " + ownId + " 引用了尚未完成的步骤 " + matcher.group(1));
        }
    }
}
