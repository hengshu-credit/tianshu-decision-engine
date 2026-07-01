package com.bjjw.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bjjw.rule.model.entity.RuleVariable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class VariableSourceResolver {

    @Resource
    private RuleVariableService variableService;

    @Resource
    private ExternalApiInvokeService externalApiInvokeService;

    @Resource
    private DBConnectPools dbConnectPools;

    @Resource
    private RuleListService ruleListService;

    public Map<String, Object> resolve(Long projectId, Map<String, Object> inputParams) {
        return resolve(projectId, inputParams, VariableResolveOptions.defaults());
    }

    public Map<String, Object> resolve(Long projectId, Map<String, Object> inputParams, VariableResolveOptions options) {
        VariableResolveOptions effectiveOptions = options == null ? VariableResolveOptions.defaults() : options;
        Map<String, Object> resolvedParams = new LinkedHashMap<>();
        if (inputParams != null) {
            resolvedParams.putAll(inputParams);
        }
        List<RuleVariable> variables = variableService.listByProject(projectId, null);
        if (variables == null || variables.isEmpty()) {
            return resolvedParams;
        }
        Map<String, Map<String, Object>> apiResponseCache = new LinkedHashMap<>();
        for (RuleVariable variable : variables) {
            if (variable == null || variable.getStatus() == null || variable.getStatus() != 1) {
                continue;
            }
            String varSource = variable.getVarSource();
            if (!"API".equals(varSource) && !"DB".equals(varSource) && !"LIST".equals(varSource)) {
                continue;
            }
            String scriptName = resolveScriptName(variable);
            if (!hasText(scriptName)) {
                continue;
            }
            Set<String> requiredScriptNames = effectiveOptions.getRequiredScriptNames();
            if (requiredScriptNames != null && !requiredScriptNames.isEmpty()
                    && !requiredScriptNames.contains(scriptName)) {
                continue;
            }
            Map<String, Object> config = parseJsonMap(variable.getSourceConfig());
            boolean forceRefresh = booleanValue(config.get("forceRefresh"));
            if (!forceRefresh && resolvedParams.containsKey(scriptName)) {
                continue;
            }
            try {
                Object value;
                if ("API".equals(varSource)) {
                    if (effectiveOptions.isSkipApiSources()) {
                        resolvedParams.put(scriptName, null);
                        continue;
                    }
                    value = resolveApiVariable(config, resolvedParams, apiResponseCache);
                } else if ("DB".equals(varSource)) {
                    value = resolveDbVariable(config, resolvedParams);
                } else {
                    value = resolveListVariable(config, resolvedParams, effectiveOptions);
                }
                if (value == null) {
                    value = parseDefaultValue(variable);
                }
                resolvedParams.put(scriptName, value);
            } catch (Exception e) {
                applyExceptionStrategy(variable, config, scriptName, resolvedParams, e);
            }
        }
        return resolvedParams;
    }

    private Object resolveApiVariable(Map<String, Object> config, Map<String, Object> params,
                                      Map<String, Map<String, Object>> apiResponseCache) {
        Long apiConfigId = longValue(config.get("apiConfigId"));
        if (apiConfigId == null) {
            throw new IllegalArgumentException("API变量缺少 apiConfigId");
        }
        Map<String, Object> requestParams = buildMappedParams(config.get("paramMapping"), params);
        String cacheKey = apiConfigId + ":" + JSON.toJSONString(requestParams);
        Map<String, Object> response = apiResponseCache.get(cacheKey);
        if (response == null) {
            response = externalApiInvokeService.invoke(apiConfigId, requestParams);
            apiResponseCache.put(cacheKey, response);
        }
        String resultPath = stringValue(config.get("resultPath"));
        return hasText(resultPath) ? readPath(response, resultPath) : response.get("body");
    }

    private Object resolveDbVariable(Map<String, Object> config, Map<String, Object> params) throws Exception {
        Long datasourceId = longValue(config.get("datasourceId"));
        String sql = stringValue(config.get("sql"));
        if (datasourceId == null) {
            throw new IllegalArgumentException("DB变量缺少 datasourceId");
        }
        if (!hasText(sql)) {
            throw new IllegalArgumentException("DB变量缺少查询SQL");
        }
        List<Object> queryParams = buildParamList(config.get("params"), params);
        int maxRows = intValue(config.get("maxRows"), 1);
        List<Map<String, Object>> rows = dbConnectPools.query(datasourceId, sql, queryParams, maxRows);
        String resultPath = stringValue(config.get("resultPath"));
        if (hasText(resultPath)) {
            return readPath(rows, resultPath);
        }
        return defaultDbValue(rows, maxRows);
    }

    private Object resolveListVariable(Map<String, Object> config, Map<String, Object> params,
                                       VariableResolveOptions options) {
        Long listId = longValue(firstNonNull(config.get("listId"), config.get("listLibraryId")));
        if (listId == null) {
            throw new IllegalArgumentException("名单变量缺少 listId");
        }
        Object queryValue = resolveListQueryValue(config, params);
        List<String> itemTypes = buildStringList(firstNonNull(config.get("itemTypes"), config.get("itemType")));
        String matchMode = stringValue(firstNonNull(config.get("matchMode"), config.get("operator")));
        if (!hasText(matchMode)) {
            matchMode = "IN_LIST";
        }
        boolean hit = options != null && options.getListMatchTime() != null
                ? ruleListService.matchAt(listId, queryValue, itemTypes, matchMode, options.getListMatchTime())
                : ruleListService.match(listId, queryValue, itemTypes, matchMode);
        String returnMode = stringValue(config.get("returnMode"));
        if ("BOOLEAN".equals(returnMode)) {
            return hit;
        }
        if (config.containsKey("hitValue") || config.containsKey("missValue")) {
            return hit ? parseConfiguredValue(config.get("hitValue"), params) : parseConfiguredValue(config.get("missValue"), params);
        }
        return hit ? 1 : 0;
    }

    private Object resolveListQueryValue(Map<String, Object> config, Map<String, Object> params) {
        Object raw = firstNonNull(config.get("queryField"), config.get("queryPath"), config.get("field"));
        if (raw == null) {
            throw new IllegalArgumentException("名单变量缺少 queryField");
        }
        if (raw instanceof String) {
            String text = (String) raw;
            if (text.startsWith("$.")) {
                return readPath(params, text.substring(2));
            }
            Object value = readPath(params, text);
            return value != null ? value : parseConfiguredValue(raw, params);
        }
        return parseConfiguredValue(raw, params);
    }

    private void applyExceptionStrategy(RuleVariable variable, Map<String, Object> config, String scriptName,
                                        Map<String, Object> resolvedParams, Exception e) {
        String strategy = stringValue(config.get("exceptionStrategy"));
        if (!hasText(strategy)) {
            strategy = "ERROR";
        }
        if ("SKIP".equals(strategy)) {
            return;
        }
        if ("RETURN_DEFAULT".equals(strategy)) {
            Object fallback = config.containsKey("fallbackValue")
                    ? parseConfiguredValue(config.get("fallbackValue"), resolvedParams)
                    : parseDefaultValue(variable);
            resolvedParams.put(scriptName, fallback);
            return;
        }
        throw new IllegalStateException("变量[" + variable.getVarCode() + "]外部取数失败：" + e.getMessage(), e);
    }

    private Map<String, Object> buildMappedParams(Object mappingObject, Map<String, Object> params) {
        Map<String, Object> mapping = parseNestedMap(mappingObject);
        if (mapping.isEmpty()) {
            return new LinkedHashMap<>(params);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            result.put(entry.getKey(), parseConfiguredValue(entry.getValue(), params));
        }
        return result;
    }

    private List<Object> buildParamList(Object configParams, Map<String, Object> params) {
        List<Object> result = new ArrayList<>();
        if (configParams instanceof Iterable) {
            for (Object value : (Iterable<?>) configParams) {
                result.add(parseConfiguredValue(value, params));
            }
            return result;
        }
        if (configParams != null && configParams.getClass().isArray()) {
            int length = Array.getLength(configParams);
            for (int i = 0; i < length; i++) {
                result.add(parseConfiguredValue(Array.get(configParams, i), params));
            }
        }
        return result;
    }

    private List<String> buildStringList(Object configValue) {
        List<String> result = new ArrayList<>();
        if (configValue instanceof Iterable) {
            for (Object value : (Iterable<?>) configValue) {
                if (value != null && hasText(String.valueOf(value))) {
                    result.add(String.valueOf(value));
                }
            }
            return result;
        }
        if (configValue != null && configValue.getClass().isArray()) {
            int length = Array.getLength(configValue);
            for (int i = 0; i < length; i++) {
                Object value = Array.get(configValue, i);
                if (value != null && hasText(String.valueOf(value))) {
                    result.add(String.valueOf(value));
                }
            }
            return result;
        }
        if (configValue != null && hasText(String.valueOf(configValue))) {
            String[] parts = String.valueOf(configValue).split(",");
            for (String part : parts) {
                if (hasText(part)) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }

    private Object defaultDbValue(List<Map<String, Object>> rows, int maxRows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, Object> first = rows.get(0);
        if (first == null || first.isEmpty()) {
            return null;
        }
        if (first.size() == 1) {
            return first.values().iterator().next();
        }
        return maxRows == 1 ? first : rows;
    }

    private Object parseConfiguredValue(Object value, Map<String, Object> params) {
        if (value instanceof String) {
            String text = (String) value;
            if (text.startsWith("$.")) {
                return readPath(params, text.substring(2));
            }
            if (text.contains("${")) {
                return resolveTemplate(text, params);
            }
            return parseJsonOrRaw(text);
        }
        if (value instanceof Map) {
            Map<String, Object> map = parseNestedMap(value);
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                resolved.put(entry.getKey(), parseConfiguredValue(entry.getValue(), params));
            }
            return resolved;
        }
        return value;
    }

    private Object parseDefaultValue(RuleVariable variable) {
        String defaultValue = variable.getDefaultValue();
        if (!hasText(defaultValue)) {
            return null;
        }
        String type = variable.getVarType();
        try {
            if ("INTEGER".equals(type)) {
                return Integer.valueOf(defaultValue);
            }
            if ("NUMBER".equals(type) || "DOUBLE".equals(type) || "DECIMAL".equals(type)) {
                return Double.valueOf(defaultValue);
            }
            if ("BOOLEAN".equals(type)) {
                return Boolean.valueOf(defaultValue);
            }
            return parseJsonOrRaw(defaultValue);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String resolveScriptName(RuleVariable variable) {
        String scriptName = trimToNull(variable.getScriptName());
        return scriptName != null ? scriptName : trimToNull(variable.getVarCode());
    }

    private Map<String, Object> parseJsonMap(String text) {
        if (!hasText(text)) {
            return new LinkedHashMap<>();
        }
        Object parsed = JSON.parse(text);
        return parseNestedMap(parsed);
    }

    private Map<String, Object> parseNestedMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private Object parseJsonOrRaw(String text) {
        if (!hasText(text)) {
            return text;
        }
        try {
            return JSON.parse(text);
        } catch (Exception e) {
            return text;
        }
    }

    private String resolveTemplate(String template, Map<String, Object> params) {
        String result = template;
        int start = result.indexOf("${");
        while (start >= 0) {
            int end = result.indexOf("}", start);
            if (end < 0) {
                break;
            }
            String path = result.substring(start + 2, end);
            Object value = readPath(params, path);
            result = result.substring(0, start) + (value == null ? "" : String.valueOf(value)) + result.substring(end + 1);
            start = result.indexOf("${", start + 1);
        }
        return result;
    }

    private Object readPath(Object root, String path) {
        if (root == null || !hasText(path)) {
            return root;
        }
        String normalized = path.startsWith("$.") ? path.substring(2) : path;
        Object current = root;
        String[] parts = normalized.split("\\.");
        for (String part : parts) {
            if (current instanceof JSONObject) {
                current = ((JSONObject) current).get(part);
            } else if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else if (current instanceof List) {
                Integer index = parseIndex(part);
                if (index == null || index < 0 || index >= ((List<?>) current).size()) {
                    return null;
                }
                current = ((List<?>) current).get(index);
            } else if (current != null && current.getClass().isArray()) {
                Integer index = parseIndex(part);
                if (index == null || index < 0 || index >= Array.getLength(current)) {
                    return null;
                }
                current = Array.get(current, index);
            } else {
                return null;
            }
        }
        return current;
    }

    private Integer parseIndex(String text) {
        try {
            return Integer.valueOf(text);
        } catch (Exception e) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null || !hasText(String.valueOf(value))) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null || !hasText(String.valueOf(value))) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
