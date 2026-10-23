package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 无网络、无鉴权副作用的请求绑定。调用身份只取决于配置和生效的请求字段。 */
public record ExternalApiRequestPlan(RuleExternalApiConfig config, Map<String, Object> values,
                                     Map<String, Object> params, String identity) {
    public static ExternalApiRequestPlan prepare(RuleExternalApiConfig source, Map<String, Object> input,
                                                 Map<String, Object> overrides, Map<String, String> paths) {
        return prepare(source, input, overrides, paths, null);
    }

    public static ExternalApiRequestPlan prepare(RuleExternalApiConfig source, Map<String, Object> input,
                                                 Map<String, Object> overrides, Map<String, String> paths, String inheritedAuth) {
        return prepare(source, input, overrides, paths, inheritedAuth, Map.of());
    }

    public static ExternalApiRequestPlan prepare(RuleExternalApiConfig source, Map<String, Object> input,
                                                 Map<String, Object> overrides, Map<String, String> paths, String inheritedAuth,
                                                 Map<String, Object> linkedInputs) {
        RuleExternalApiConfig api = JSON.parseObject(JSON.toJSONString(source), RuleExternalApiConfig.class);
        JSONObject specification = specification(api);
        if (specification == null) throw new IllegalArgumentException("外数 API 必须使用 V2 executionConfig");
        List<JSONObject> fields = fields(specification);
        Map<String, Object> effective = new LinkedHashMap<>();
        Map<String, Object> headers = new LinkedHashMap<>();
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> cookies = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> context = new LinkedHashMap<>(input == null ? Map.of() : input);
        context.putIfAbsent("input", input == null ? Map.of() : input);
        Set<String> accepted = new LinkedHashSet<>();
        Set<String> bodyFormats = new LinkedHashSet<>();
        Map<String, Object> supplied = overrides == null ? Map.of() : overrides;
        for (JSONObject field : fields) {
            String id = required(field.getString("id"), "请求字段 ID");
            if (!accepted.add(id)) throw new IllegalArgumentException("重复的请求字段 ID: " + id);
            if (supplied.containsKey(id) && !field.getBooleanValue("overridable")) {
                throw new IllegalArgumentException("请求字段不允许覆盖: " + id);
            }
            Object expression = supplied.containsKey(id) ? supplied.get(id) : field.get("value");
            Object value = evaluate(expression, context, paths);
            if (value == null && !supplied.containsKey(id) && field.containsKey("defaultValue")) value = evaluate(field.get("defaultValue"), context, paths);
            if (field.getBooleanValue("required") && (value == null || value instanceof String text && text.isBlank())) {
                throw new IllegalArgumentException("API 必填请求参数缺失: " + field.getString("path"));
            }
            String nullPolicy = field.getString("nullPolicy");
            if (nullPolicy == null || "DEFAULT".equals(nullPolicy)) nullPolicy = specification.getString("nullPolicy");
            if (value == null && !"NULL".equals(nullPolicy) && !"EMPTY".equals(nullPolicy)) continue;
            if (value == null && "EMPTY".equals(nullPolicy)) value = "";
            String path = required(field.getString("path"), "请求字段路径");
            String location = required(field.getString("location"), "请求字段位置");
            if (value == null && !"JSON".equals(location)) value = "";
            effective.put(id, value);
            if (Set.of("JSON", "FORM", "FORM_DATA").contains(location)) bodyFormats.add(location);
            String reference = "$.__apiFields." + id;
            switch (location) {
                case "HEADER" -> headers.put(path, reference);
                case "QUERY" -> query.put(path, reference);
                case "COOKIE" -> cookies.put(path, reference);
                case "JSON" -> write(body, path, reference);
                case "FORM", "FORM_DATA" -> body.put(path, reference);
                default -> throw new IllegalArgumentException("不支持的请求字段位置: " + location);
            }
        }
        for (String id : supplied.keySet()) {
            if (!accepted.contains(id)) throw new IllegalArgumentException("请求覆盖引用了不存在的字段 ID: " + id);
        }
        if (specification.containsKey("requestFields")) {
            if (bodyFormats.size() > 1) throw new IllegalArgumentException("同一次请求体不能混合 JSON、FORM 和 FORM_DATA 字段");
            if (bodyFormats.contains("FORM")) api.setContentType("application/x-www-form-urlencoded");
            if (bodyFormats.contains("FORM_DATA")) api.setContentType("multipart/form-data");
            // 已逐字段执行传空策略，禁止后续默认策略重新删除显式 null。
            body.put("_nullPolicy", "SEND_ALL");
            api.setHeaderConfig(JSON.toJSONString(headers));
            api.setQueryConfig(JSON.toJSONString(query));
            if (!cookies.isEmpty()) body.put("_cookies", cookies);
            api.setRequestMapping(JSON.toJSONString(body));
            api.setBodyTemplate("{}");
            Map<String, Object> params = new LinkedHashMap<>(context);
            params.put("__apiFields", effective);
            Map<String, Object> identityValues = new LinkedHashMap<>(effective);
            collectStepInputs(specification.get("steps"), context, paths, identityValues, effective);
            Map<String, Object> authInputs = new LinkedHashMap<>();
            Map<String, Object> stepAuth = new LinkedHashMap<>();
            if (specification.get("steps") instanceof Iterable<?> steps) for (Object raw : steps) {
                if (!(raw instanceof Map<?, ?> step)) continue;
                Map<String, Object> definition = new LinkedHashMap<>();
                for (String key : List.of("authMode", "authApiConfig", "authApiConfigId")) definition.put(key, step.get(key));
                stepAuth.put(String.valueOf(step.get("id")), definition);
            }
            authInputs.put("stepDefinitions", stepAuth);
            collectStepInputs(stepAuth, context, paths, authInputs, effective);
            authInputs.put("linkedDefinitions", linkedInputs);
            collectStepInputs(linkedInputs, context, paths, authInputs, effective);
            authInputs.put("apiDefinition", source.getAuthApiConfig());
            collectStepInputs(ExternalApiAuthConfig.inputs(source.getAuthApiConfig()), context, paths, authInputs, effective);
            if (source.getAuthMode() == null || "INHERIT".equalsIgnoreCase(source.getAuthMode())) {
                authInputs.put("definition", inheritedAuth);
                collectStepInputs(ExternalApiAuthConfig.inputs(inheritedAuth), context, paths, authInputs, effective);
            }
            identityValues.put("authentication", authInputs);
            params.put("__apiAuthIdentity", Sha256Digests.text(CanonicalJson.write(authInputs)));
            return new ExternalApiRequestPlan(api, effective, params, identity(source, identityValues));
        }
        throw new IllegalArgumentException("V2 executionConfig 缺少 requestFields");
    }

    public static void validateBinding(Map<?, ?> binding) {
        if (binding.containsKey("inputOverrides") || binding.containsKey("paramMapping")) {
            throw new IllegalArgumentException("外数取值仅支持按请求字段 ID 配置 requestOverrides");
        }
    }

    private static void collectStepInputs(Object node, Map<String, Object> context, Map<String, String> paths,
                                         Map<String, Object> values, Map<String, Object> boundFields) {
        if (node instanceof Map<?, ?> map) {
            if ("LITERAL".equals(map.get("kind"))) return;
            if (map.get("refId") != null && map.get("refType") != null) {
                // 同一资源可以引用不同子路径或版本，不能仅按资源 ID 覆盖前一个取值。
                values.put("reference:" + CanonicalJson.write(map), evaluate(node, context, paths));
                return;
            }
            if ("PATH".equals(map.get("kind")) && map.get("value") instanceof String path
                    && path.startsWith("input.__apiFields.")) {
                values.put("boundInput:" + path, read(boundFields, path.substring("input.__apiFields.".length())));
                return;
            }
            map.forEach((key, value) -> {
                if ("authApiConfig".equals(key) && value instanceof String text) collectStepInputs(ExternalApiAuthConfig.inputs(text), context, paths, values, boundFields);
                else if (!Set.of("sample", "samples", "requestSample", "responseSample", "responseBranches").contains(String.valueOf(key))) collectStepInputs(value, context, paths, values, boundFields);
            });
        } else if (node instanceof Iterable<?> list) list.forEach(child -> collectStepInputs(child, context, paths, values, boundFields));
    }

    public static JSONObject specification(RuleExternalApiConfig api) {
        return api.getExecutionConfig() == null || api.getExecutionConfig().isBlank()
                ? null : JSON.parseObject(api.getExecutionConfig());
    }

    public static List<JSONObject> fields(JSONObject specification) {
        List<JSONObject> result = new ArrayList<>();
        if (specification != null && specification.getJSONArray("requestFields") != null) {
            for (Object raw : specification.getJSONArray("requestFields")) result.add(JSON.parseObject(JSON.toJSONString(raw)));
        }
        return result;
    }

    /** 管理资源必须按 ID 解析；协议内路径由已声明的步骤/响应上下文提供。 */
    public static Object evaluate(Object expression, Map<String, Object> context, Map<String, String> paths) {
        if (expression == null) return null;
        if (!(expression instanceof Map<?, ?>)) return expression;
        JSONObject operand = JSON.parseObject(JSON.toJSONString(expression));
        if (!(operand.get("kind") instanceof String)) {
            Map<String, Object> result = new LinkedHashMap<>();
            operand.forEach((key, value) -> result.put(key, evaluate(value, context, paths)));
            return result;
        }
        validateReferences(operand, context, paths == null ? Map.of() : paths);
        Map<String, Object> references = OperandValueResolver.buildReferenceValues(paths, context, Map.of());
        if (paths != null) paths.forEach((key, path) -> references.put(key, read(context, path)));
        Map<String, Object> values = new LinkedHashMap<>(context);
        materializePaths(operand, context, values);
        return OperandValueResolver.resolve(operand, values, references);
    }

    private static void materializePaths(Object node, Map<String, Object> context, Map<String, Object> values) {
        if (node instanceof Map<?, ?> map) {
            if ("PATH".equals(map.get("kind")) && map.get("refId") == null) {
                String path = String.valueOf(map.get("value"));
                values.put(path, read(context, path));
            }
            map.values().forEach(child -> materializePaths(child, context, values));
        } else if (node instanceof Iterable<?> list) list.forEach(child -> materializePaths(child, context, values));
    }

    private static void validateReferences(Object value, Map<String, Object> context, Map<String, String> paths) {
        if (value instanceof Map<?, ?> raw) {
            JSONObject node = JSON.parseObject(JSON.toJSONString(raw));
            ExternalApiExecutionConfig.validateFunction(node);
            String kind = node.getString("kind");
            if ("LITERAL".equals(kind)) return;
            if (com.hengshucredit.rule.core.compiler.OperandVersion.fixedVersionId(node) != null) {
                if ("FUNCTION".equals(kind)) validateReferences(node.get("args"), context, paths);
                return;
            }
            if ("REFERENCE".equals(kind) || "PATH".equals(kind) && node.get("refId") != null) {
                String key = node.getString("refType") + ":" + node.getString("refId");
                if (!paths.containsKey(key)) throw new IllegalArgumentException("API 引用字段无法按 ID 解析: " + key);
            } else if ("PATH".equals(kind)) {
                String path = required(node.getString("value"), "字段路径");
                boolean protocol = "$".equals(path) || path.matches("(?:request|response|authentication|steps|callbacks|status|body|headers|rawBody)(?:[.\\[].+)?|input\\.__apiFields\\..+|httpStatus|callId|costTimeMs|errorMessage|success");
                if (!protocol) throw new IllegalArgumentException("引擎字段必须通过稳定 ID 引用: " + path);
            }
            raw.values().forEach(item -> validateReferences(item, context, paths));
        } else if (value instanceof Iterable<?> list) {
            list.forEach(item -> validateReferences(item, context, paths));
        }
    }

    public static Object read(Object root, String path) {
        if (path == null || path.isBlank() || "$".equals(path)) return root;
        if (root instanceof Map<?, ?> map && map.containsKey(path)) return map.get(path);
        String jsonPath = path.startsWith("$") ? path : "$." + path;
        Object value = JSONPath.eval(root, jsonPath);
        return value != null ? value : JSONPath.eval(root, jsonPath.replaceAll("\\.(\\d+)(?=\\.|\\[|$)", "[$1]"));
    }

    public static boolean present(Object root, String path) {
        if (path == null || path.isBlank()) return false;
        if (root instanceof Map<?, ?> map && map.containsKey(path)) return true;
        String jsonPath = path.startsWith("$") ? path : "$." + path;
        return JSONPath.contains(root, jsonPath) || JSONPath.contains(root, jsonPath.replaceAll("\\.(\\d+)(?=\\.|\\[|$)", "[$1]"));
    }

    @SuppressWarnings("unchecked")
    public static void write(Map<String, Object> root, String path, Object value) {
        String target = path.startsWith("$") ? path : path.startsWith("[") ? "$" + path : "$." + path;
        if (!JSONPath.set(root, target, value)) throw new IllegalArgumentException("字段路径无法写入: " + path);
    }

    private static String identity(RuleExternalApiConfig api, Map<String, Object> fields) {
        Map<String, Object> source = JSON.parseObject(JSON.toJSONString(api));
        source.remove("createTime"); source.remove("updateTime"); source.remove("testSampleParams");
        source.remove("executionCallId");
        return "API_CHAIN:" + api.getId() + ":" + Sha256Digests.text(CanonicalJson.write(source))
                + ":" + Sha256Digests.text(CanonicalJson.write(fields));
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value;
    }
}
