package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.model.entity.RuleVariable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
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

    @Resource
    private RuleRuntimeCallLogService runtimeCallLogService;

    @Resource
    private RuleDbDatasourceService dbDatasourceService;

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
            boolean forceRefresh = effectiveOptions.isForceRefreshSource() || booleanValue(config.get("forceRefresh"));
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
                    value = resolveDbVariable(variable, config, resolvedParams);
                } else {
                    value = resolveListVariable(variable, config, resolvedParams, effectiveOptions);
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

    public Map<String, Object> testVariable(Long variableId, Map<String, Object> inputParams) {
        if (variableId == null) {
            throw new IllegalArgumentException("变量ID不能为空");
        }
        RuleVariable variable = variableService.getById(variableId);
        if (variable == null) {
            throw new IllegalArgumentException("变量不存在");
        }
        String varSource = variable.getVarSource();
        if (!"API".equals(varSource) && !"DB".equals(varSource) && !"LIST".equals(varSource)) {
            throw new IllegalArgumentException("仅支持测试 API、数据库、名单变量");
        }
        String scriptName = resolveScriptName(variable);
        if (!hasText(scriptName)) {
            throw new IllegalArgumentException("变量缺少脚本名称");
        }
        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setForceRefreshSource(true);
        options.setRequiredScriptNames(new LinkedHashSet<>(Collections.singletonList(scriptName)));
        Map<String, Object> params = inputParams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputParams);
        Map<String, Object> resolved = resolve(variable.getProjectId(), params, options);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("variableId", variable.getId());
        result.put("varCode", variable.getVarCode());
        result.put("scriptName", scriptName);
        result.put("varSource", varSource);
        result.put("inputParams", params);
        result.put("resolvedValue", resolved.get(scriptName));
        result.put("resolvedParams", resolved);
        return result;
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

    private Object resolveDbVariable(RuleVariable variable, Map<String, Object> config, Map<String, Object> params) throws Exception {
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
        long start = System.currentTimeMillis();
        LocalDateTime startTime = LocalDateTime.now();
        RuleDbDatasource datasource = loadDbDatasource(datasourceId);
        Map<String, Object> request = buildDbLogRequest(datasource, variable, config, sql, queryParams, maxRows, startTime);
        try {
            List<Map<String, Object>> rows = dbConnectPools.query(datasourceId, sql, queryParams, maxRows);
            String resultPath = stringValue(config.get("resultPath"));
            Object extracted = null;
            if (hasText(resultPath)) {
                extracted = readPath(rows, resultPath);
            } else {
                extracted = defaultDbValue(rows, maxRows);
            }
            Map<String, Object> response = buildDbLogResponse("SUCCESS", rows, resultPath, extracted, startTime, null);
            logVariableSource("DATABASE", "DB_VARIABLE_QUERY", variable, datasourceId, request, response, null, System.currentTimeMillis() - start);
            return extracted;
        } catch (Exception e) {
            Map<String, Object> response = buildDbLogResponse("FAILED", null, stringValue(config.get("resultPath")), null, startTime, e.getMessage());
            logVariableSource("DATABASE", "DB_VARIABLE_QUERY", variable, datasourceId, request, response, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        }
    }

    private RuleDbDatasource loadDbDatasource(Long datasourceId) {
        if (dbDatasourceService == null || datasourceId == null) {
            return null;
        }
        try {
            return dbDatasourceService.getById(datasourceId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> buildDbLogRequest(RuleDbDatasource datasource, RuleVariable variable,
            Map<String, Object> config, String sql, List<Object> queryParams, int maxRows, LocalDateTime startTime) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("connectionMode", datasource == null ? null : datasource.getConnectionMode());
        request.put("dbType", datasource == null ? null : datasource.getDbType());
        request.put("datasourceId", datasource == null ? longValue(config.get("datasourceId")) : datasource.getId());
        request.put("datasourceCode", datasource == null ? null : datasource.getDatasourceCode());
        request.put("datasourceName", datasource == null ? null : datasource.getDatasourceName());
        request.put("targetVariable", variable == null ? null : resolveScriptName(variable));
        request.put("queryStatus", "RUNNING");
        request.put("startTime", startTime == null ? null : startTime.toString());
        request.put("sql", sql);
        request.put("params", queryParams);
        request.put("paramFields", buildDbParamFields(config.get("params"), queryParams));
        request.put("maxRows", maxRows);
        return request;
    }

    private Map<String, Object> buildDbLogResponse(String status, List<Map<String, Object>> rows, String resultPath,
            Object extracted, LocalDateTime startTime, String errorMessage) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("queryStatus", status);
        response.put("startTime", startTime == null ? null : startTime.toString());
        response.put("endTime", LocalDateTime.now().toString());
        response.put("rowCount", rows == null ? 0 : rows.size());
        response.put("rows", rows);
        response.put("resultPath", resultPath);
        response.put("extractedValue", extracted);
        if (errorMessage != null) {
            response.put("errorMessage", errorMessage);
        }
        return response;
    }

    private List<Map<String, Object>> buildDbParamFields(Object configParams, List<Object> queryParams) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Object> rawParams = new ArrayList<>();
        if (configParams instanceof Iterable) {
            for (Object value : (Iterable<?>) configParams) {
                rawParams.add(value);
            }
        } else if (configParams != null && configParams.getClass().isArray()) {
            int length = Array.getLength(configParams);
            for (int i = 0; i < length; i++) {
                rawParams.add(Array.get(configParams, i));
            }
        }
        int size = Math.max(rawParams.size(), queryParams == null ? 0 : queryParams.size());
        for (int i = 0; i < size; i++) {
            Object raw = i < rawParams.size() ? rawParams.get(i) : null;
            Object value = queryParams != null && i < queryParams.size() ? queryParams.get(i) : null;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", i + 1);
            item.put("field", dbParamFieldName(raw));
            item.put("expression", raw);
            item.put("value", value);
            result.add(item);
        }
        return result;
    }

    private String dbParamFieldName(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.startsWith("$.")) {
            return text.substring(2);
        }
        if (text.startsWith("${") && text.endsWith("}")) {
            return text.substring(2, text.length() - 1);
        }
        return text;
    }

    private Object resolveListVariable(RuleVariable variable, Map<String, Object> config, Map<String, Object> params,
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
        long start = System.currentTimeMillis();
        boolean hit = options != null && options.getListMatchTime() != null
                ? ruleListService.matchAt(listId, queryValue, itemTypes, matchMode, options.getListMatchTime())
                : ruleListService.match(listId, queryValue, itemTypes, matchMode);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("queryValue", queryValue);
        request.put("itemTypes", itemTypes);
        request.put("matchMode", matchMode);
        if (options != null && options.getListMatchTime() != null) {
            request.put("matchTime", options.getListMatchTime());
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("hit", hit);
        logVariableSource("LIST", "LIST_VARIABLE_MATCH", variable, listId, request, response, null, System.currentTimeMillis() - start);
        String returnMode = stringValue(config.get("returnMode"));
        if ("BOOLEAN".equals(returnMode)) {
            return hit;
        }
        if (config.containsKey("hitValue") || config.containsKey("missValue")) {
            return hit ? parseConfiguredValue(config.get("hitValue"), params) : parseConfiguredValue(config.get("missValue"), params);
        }
        return hit ? 1 : 0;
    }

    private void logVariableSource(String moduleType, String actionType, RuleVariable variable, Long targetId,
                                   Object requestBody, Object responseBody, String errorMessage, long costTimeMs) {
        if (runtimeCallLogService == null || variable == null) {
            return;
        }
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        log.setModuleType(moduleType);
        log.setActionType(actionType);
        log.setProjectId(variable.getProjectId());
        log.setTargetRefId(targetId);
        log.setTargetCode(resolveScriptName(variable));
        log.setTargetName(variable.getVarLabel());
        log.setSuccess(errorMessage == null ? 1 : 0);
        log.setRequestMethod(resolveRuntimeLogMethod(moduleType));
        log.setResponseStatus(errorMessage == null ? 200 : 500);
        log.setRequestBody(runtimeCallLogService.toJson(requestBody));
        log.setResponseBody(runtimeCallLogService.toJson(responseBody));
        log.setErrorMessage(errorMessage);
        log.setCostTimeMs(costTimeMs);
        runtimeCallLogService.safeSave(log);
    }

    private String resolveRuntimeLogMethod(String moduleType) {
        if ("DATABASE".equals(moduleType)) {
            return "SQL";
        }
        if ("LIST".equals(moduleType)) {
            return "MATCH";
        }
        return "HTTP";
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
