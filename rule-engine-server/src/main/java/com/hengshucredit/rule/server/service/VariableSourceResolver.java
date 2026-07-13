package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
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
    private RuleExternalApiConfigMapper apiConfigMapper;

    @Resource
    private DBConnectPools dbConnectPools;

    @Resource
    private RuleListService ruleListService;

    @Resource
    private RuleRuntimeCallLogService runtimeCallLogService;

    @Resource
    private RuleDbDatasourceService dbDatasourceService;

    @Resource
    private RuleModelService ruleModelService;

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
        if (variables == null) variables = Collections.emptyList();
        List<RuleModel> models = loadProjectModels(projectId);
        Set<String> requiredScriptNames = expandRequiredScriptNames(effectiveOptions.getRequiredScriptNames(), variables, models);
        Map<String, Map<String, Object>> apiResponseCache = new LinkedHashMap<>();
        resolveVariablesAndModels(variables, models, requiredScriptNames, resolvedParams, effectiveOptions, apiResponseCache);
        return resolvedParams;
    }

    private void resolveVariablesAndModels(List<RuleVariable> variables, List<RuleModel> models,
                                           Set<String> requiredScriptNames, Map<String, Object> resolvedParams,
                                           VariableResolveOptions effectiveOptions,
                                           Map<String, Map<String, Object>> apiResponseCache) {
        Map<String, RuleVariable> variableMap = buildVariableMap(variables);
        Map<String, RuleModel> modelMap = buildModelMap(models);
        List<RuleVariable> pendingVariables = collectPendingVariables(variables, requiredScriptNames, resolvedParams, effectiveOptions);
        List<RuleModel> pendingModels = collectPendingModels(models, requiredScriptNames, resolvedParams, effectiveOptions);
        while (!pendingVariables.isEmpty() || !pendingModels.isEmpty()) {
            boolean progressed = false;
            List<RuleVariable> delayedVariables = new ArrayList<>();
            for (RuleVariable variable : pendingVariables) {
                String scriptName = resolveScriptName(variable);
                if (hasUnresolvedResolvableDependency(scriptName, collectVariableDependencies(variable),
                        variableMap, modelMap, requiredScriptNames, resolvedParams, effectiveOptions)) {
                    delayedVariables.add(variable);
                    continue;
                }
                resolveOneVariable(variable, scriptName, resolvedParams, effectiveOptions, apiResponseCache);
                progressed = true;
            }
            pendingVariables = delayedVariables;

            List<RuleModel> delayedModels = new ArrayList<>();
            for (RuleModel model : pendingModels) {
                String modelCode = trimToNull(model.getModelCode());
                if (hasUnresolvedResolvableDependency(modelCode, collectModelInputNames(model),
                        variableMap, modelMap, requiredScriptNames, resolvedParams, effectiveOptions)) {
                    delayedModels.add(model);
                    continue;
                }
                resolveOneModel(model, modelCode, resolvedParams);
                progressed = true;
            }
            pendingModels = delayedModels;

            if (!progressed) {
                throw new IllegalStateException("变量/模型依赖存在循环或无法解析：" + pendingNames(pendingVariables, pendingModels));
            }
        }
    }

    private List<RuleVariable> collectPendingVariables(List<RuleVariable> variables, Set<String> requiredScriptNames,
                                                       Map<String, Object> resolvedParams, VariableResolveOptions options) {
        List<RuleVariable> pending = new ArrayList<>();
        if (variables == null || variables.isEmpty()) {
            return pending;
        }
        for (RuleVariable variable : variables) {
            String scriptName = resolveScriptName(variable);
            if (!shouldResolveVariable(variable, scriptName, requiredScriptNames)) {
                continue;
            }
            if (!shouldRefreshVariable(variable, scriptName, resolvedParams, options)) {
                continue;
            }
            pending.add(variable);
        }
        return pending;
    }

    private List<RuleModel> collectPendingModels(List<RuleModel> models, Set<String> requiredScriptNames,
                                                 Map<String, Object> resolvedParams, VariableResolveOptions options) {
        List<RuleModel> pending = new ArrayList<>();
        if (ruleModelService == null || models == null || models.isEmpty()
                || requiredScriptNames == null || requiredScriptNames.isEmpty()) {
            return pending;
        }
        for (RuleModel model : models) {
            String modelCode = trimToNull(model.getModelCode());
            if (modelCode == null || !isModelRequired(modelCode, requiredScriptNames)) {
                continue;
            }
            if (!shouldRefreshModel(modelCode, resolvedParams, options)) {
                continue;
            }
            pending.add(model);
        }
        return pending;
    }

    private void resolveOneVariable(RuleVariable variable, String scriptName, Map<String, Object> resolvedParams,
                                    VariableResolveOptions effectiveOptions,
                                    Map<String, Map<String, Object>> apiResponseCache) {
        if ("CONSTANT".equals(variable.getVarSource())) {
            resolvedParams.put(scriptName, parseDefaultValue(variable));
            return;
        }
        resolveOneSourceVariable(variable, scriptName, parseJsonMap(variable.getSourceConfig()),
                resolvedParams, effectiveOptions, apiResponseCache);
    }

    private void resolveOneSourceVariable(RuleVariable variable, String scriptName, Map<String, Object> config,
                                          Map<String, Object> resolvedParams, VariableResolveOptions effectiveOptions,
                                          Map<String, Map<String, Object>> apiResponseCache) {
        try {
            Object value;
            String varSource = variable.getVarSource();
            if ("API".equals(varSource)) {
                if (effectiveOptions.isSkipApiSources()) {
                    resolvedParams.put(scriptName, null);
                    return;
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

    private boolean shouldResolveVariable(RuleVariable variable, String scriptName, Set<String> requiredScriptNames) {
        if (variable == null || variable.getStatus() == null || variable.getStatus() != 1 || !hasText(scriptName)) {
            return false;
        }
        String varSource = variable.getVarSource();
        if (!"API".equals(varSource) && !"DB".equals(varSource) && !"LIST".equals(varSource) && !"CONSTANT".equals(varSource)) {
            return false;
        }
        return requiredScriptNames == null || requiredScriptNames.isEmpty() || requiredScriptNames.contains(scriptName);
    }

    private boolean shouldRefreshVariable(RuleVariable variable, String scriptName, Map<String, Object> resolvedParams,
                                          VariableResolveOptions options) {
        if (variable == null || scriptName == null) {
            return false;
        }
        if (options != null && options.isForceRefreshSource()) {
            return true;
        }
        Map<String, Object> config = parseJsonMap(variable.getSourceConfig());
        if (booleanValue(config.get("forceRefresh"))) {
            return true;
        }
        return !resolvedParams.containsKey(scriptName);
    }

    private boolean hasUnresolvedResolvableDependency(String ownName, Set<String> dependencies,
                                                      Map<String, RuleVariable> variableMap,
                                                      Map<String, RuleModel> modelMap,
                                                      Set<String> requiredScriptNames,
                                                      Map<String, Object> resolvedParams,
                                                      VariableResolveOptions options) {
        for (String dependency : dependencies) {
            if (!hasText(dependency) || dependency.equals(ownName)) {
                continue;
            }
            RuleVariable dependencyVariable = variableMap.get(dependency);
            if (dependencyVariable != null) {
                String scriptName = resolveScriptName(dependencyVariable);
                if (shouldResolveVariable(dependencyVariable, scriptName, requiredScriptNames)
                        && shouldRefreshVariable(dependencyVariable, scriptName, resolvedParams, options)) {
                    return true;
                }
            }
            RuleModel dependencyModel = findRequiredModel(dependency, modelMap);
            String modelCode = dependencyModel == null ? null : trimToNull(dependencyModel.getModelCode());
            if (modelCode != null && !modelCode.equals(ownName) && isModelRequired(modelCode, requiredScriptNames)
                    && shouldRefreshModel(modelCode, resolvedParams, options)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, RuleVariable> buildVariableMap(List<RuleVariable> variables) {
        Map<String, RuleVariable> map = new LinkedHashMap<>();
        if (variables == null) {
            return map;
        }
        for (RuleVariable variable : variables) {
            String scriptName = resolveScriptName(variable);
            if (hasText(scriptName)) {
                map.put(scriptName, variable);
            }
        }
        return map;
    }

    private Map<String, RuleModel> buildModelMap(List<RuleModel> models) {
        Map<String, RuleModel> map = new LinkedHashMap<>();
        if (models == null) {
            return map;
        }
        for (RuleModel model : models) {
            String modelCode = trimToNull(model.getModelCode());
            if (modelCode != null) {
                map.put(modelCode, model);
            }
        }
        return map;
    }

    private Set<String> expandRequiredScriptNames(Set<String> initial, List<RuleVariable> variables, List<RuleModel> models) {
        if (initial == null || initial.isEmpty()) {
            return initial;
        }
        Map<String, RuleVariable> variableMap = new LinkedHashMap<>();
        for (RuleVariable variable : variables) {
            String scriptName = resolveScriptName(variable);
            if (hasText(scriptName)) {
                variableMap.put(scriptName, variable);
            }
        }
        Map<String, RuleModel> modelMap = new LinkedHashMap<>();
        for (RuleModel model : models) {
            String modelCode = trimToNull(model.getModelCode());
            if (modelCode != null) {
                modelMap.put(modelCode, model);
            }
        }
        Set<String> expanded = new LinkedHashSet<>(initial);
        addModelsSatisfiedByRequiredInputs(expanded, modelMap);
        List<String> queue = new ArrayList<>(initial);
        int index = 0;
        while (index < queue.size()) {
            String name = queue.get(index++);
            RuleVariable variable = variableMap.get(name);
            if (variable != null) {
                for (String dependency : collectVariableDependencies(variable)) {
                    if (expanded.add(dependency)) {
                        queue.add(dependency);
                    }
                }
            }
            RuleModel model = findRequiredModel(name, modelMap);
            if (model != null) {
                for (String dependency : collectModelInputNames(model)) {
                    if (expanded.add(dependency)) {
                        queue.add(dependency);
                    }
                }
                addModelsSatisfiedByRequiredInputs(expanded, modelMap);
            }
        }
        return expanded;
    }

    private void addModelsSatisfiedByRequiredInputs(Set<String> requiredNames, Map<String, RuleModel> modelMap) {
        if (requiredNames == null || requiredNames.isEmpty() || modelMap == null || modelMap.isEmpty()) {
            return;
        }
        boolean changed;
        do {
            changed = false;
            for (RuleModel model : modelMap.values()) {
                String modelCode = trimToNull(model.getModelCode());
                if (modelCode == null || isModelRequired(modelCode, requiredNames)) {
                    continue;
                }
                Set<String> inputNames = collectModelInputNames(model);
                if (!inputNames.isEmpty() && requiredContainsAllModelInputs(modelCode, inputNames, requiredNames)) {
                    changed = requiredNames.add(modelCode) || changed;
                }
            }
        } while (changed);
    }

    private boolean requiredContainsAllModelInputs(String modelCode, Set<String> inputNames, Set<String> requiredNames) {
        for (String inputName : inputNames) {
            if (!requiredContainsModelInput(modelCode, inputName, requiredNames)) {
                return false;
            }
        }
        return true;
    }

    private boolean requiredContainsModelInput(String modelCode, String inputName, Set<String> requiredNames) {
        if (!hasText(inputName)) {
            return false;
        }
        if (requiredNames.contains(inputName)) {
            return true;
        }
        String modelFieldsName = modelCode == null ? null : modelCode + "_fields." + inputName;
        for (String required : requiredNames) {
            if (required == null) {
                continue;
            }
            if (modelFieldsName != null && required.equals(modelFieldsName)) {
                return true;
            }
            if (required.endsWith("." + inputName)) {
                return true;
            }
        }
        return false;
    }

    private RuleModel findRequiredModel(String requiredName, Map<String, RuleModel> modelMap) {
        if (!hasText(requiredName) || modelMap == null || modelMap.isEmpty()) {
            return null;
        }
        RuleModel exact = modelMap.get(requiredName);
        if (exact != null) {
            return exact;
        }
        int dotIndex = requiredName.indexOf('.');
        if (dotIndex > 0) {
            return modelMap.get(requiredName.substring(0, dotIndex));
        }
        return null;
    }

    Set<String> collectVariableDependencies(RuleVariable variable) {
        Set<String> dependencies = new LinkedHashSet<>();
        if (variable == null) {
            return dependencies;
        }
        Map<String, Object> config = parseJsonMap(variable.getSourceConfig());
        String varSource = variable.getVarSource();
        if ("API".equals(varSource)) {
            Object mapping = config.get("paramMapping");
            collectDependencyValues(mapping, dependencies);
            collectApiConfigDependencies(config.get("apiConfigId"), dependencies);
        } else if ("DB".equals(varSource)) {
            collectDependencyValues(config.get("params"), dependencies);
        } else if ("LIST".equals(varSource)) {
            collectDependencyValues(firstNonNull(config.get("queryField"), config.get("queryPath"), config.get("field")), dependencies);
        }
        return dependencies;
    }

    private void collectApiConfigDependencies(Object apiConfigIdValue, Set<String> dependencies) {
        Long apiConfigId = longValue(apiConfigIdValue);
        if (apiConfigId == null || apiConfigMapper == null) {
            return;
        }
        RuleExternalApiConfig apiConfig = apiConfigMapper.selectById(apiConfigId);
        if (apiConfig == null) {
            return;
        }
        collectDependencyValues(parseJsonMap(apiConfig.getHeaderConfig()), dependencies);
        collectDependencyValues(parseJsonMap(apiConfig.getQueryConfig()), dependencies);
        collectDependencyValues(parseJsonMap(apiConfig.getRequestMapping()), dependencies);
        collectDependencyValues(parseJsonOrRaw(apiConfig.getBodyTemplate()), dependencies);
        collectDependencyValues(parseJsonMap(apiConfig.getAuthApiConfig()), dependencies);
    }

    private Set<String> collectModelInputNames(RuleModel model) {
        Set<String> names = new LinkedHashSet<>();
        RuleModel detail = loadModelDetail(model);
        List<RuleModelInputField> fields = detail == null ? null : detail.getInputFields();
        if (fields != null) {
            for (RuleModelInputField field : fields) {
                names.addAll(OperandValueResolver.collectPaths(field.getSourceOperand()));
                names.addAll(OperandValueResolver.collectPaths(field.getDefaultOperand()));
                String scriptName = firstText(field.getScriptName(), field.getFieldName());
                if (scriptName != null) {
                    names.add(scriptName);
                }
            }
        }
        String modelCode = detail == null ? null : trimToNull(detail.getModelCode());
        List<RuleModelOutputField> outputFields = detail == null ? null : detail.getOutputFields();
        if (outputFields != null) {
            for (RuleModelOutputField field : outputFields) {
                for (String path : OperandValueResolver.collectPaths(field.getTransformOperand())) {
                    if (modelCode == null || (!path.equals(modelCode) && !path.startsWith(modelCode + "."))) {
                        names.add(path);
                    }
                }
            }
        }
        return names;
    }

    private void resolveOneModel(RuleModel model, String modelCode, Map<String, Object> resolvedParams) {
        RuleModel detail = loadModelDetail(model);
        Map<String, Object> modelParams = buildModelParams(detail, resolvedParams);
        Map<String, Object> modelResult = ruleModelService.execute(model.getId(), modelParams);
        if (!modelSucceeded(modelResult)) {
            throw new IllegalStateException("模型[" + modelCode + "]执行失败：" + modelError(modelResult));
        }
        Object outputs = modelResult.get("outputs");
        Object modelValue = outputs instanceof Map ? outputs : modelResult;
        resolvedParams.put(modelCode, modelValue);
        if (modelValue instanceof Map && detail != null && detail.getOutputFields() != null) {
            Map<?, ?> outputValues = (Map<?, ?>) modelValue;
            for (RuleModelOutputField field : detail.getOutputFields()) {
                Object value = outputValues.get(firstText(field.getFieldName(), field.getScriptName()));
                if (value == null && hasText(field.getScriptName())) value = outputValues.get(field.getScriptName());
                if (value == null && hasText(field.getFeatureName())) value = outputValues.get(field.getFeatureName());
                OperandValueResolver.write(field.getTargetOperand(), resolvedParams, value);
            }
        }
    }

    private void collectDependencyValues(Object value, Set<String> dependencies) {
        if (value == null) {
            return;
        }
        if (value instanceof Map) {
            for (Object nested : ((Map<?, ?>) value).values()) {
                collectDependencyValues(nested, dependencies);
            }
            return;
        }
        if (value instanceof Iterable) {
            for (Object nested : (Iterable<?>) value) {
                collectDependencyValues(nested, dependencies);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                collectDependencyValues(Array.get(value, i), dependencies);
            }
            return;
        }
        if (value instanceof String) {
            collectDependencyText((String) value, dependencies);
        }
    }

    private void collectDependencyText(String text, Set<String> dependencies) {
        String value = trimToNull(text);
        if (value == null) {
            return;
        }
        if (value.startsWith("$.")) {
            dependencies.add(value.substring(2));
            return;
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            dependencies.add(value.substring(2, value.length() - 1));
            return;
        }
        int start = value.indexOf("${");
        while (start >= 0) {
            int end = value.indexOf("}", start);
            if (end < 0) {
                break;
            }
            String path = value.substring(start + 2, end).trim();
            if (!path.isEmpty()) {
                dependencies.add(path);
            }
            start = value.indexOf("${", end + 1);
        }
        if (isReferencePathText(value)) {
            dependencies.add(value);
        }
    }

    private String pendingNames(List<RuleVariable> variables, List<RuleModel> models) {
        List<String> names = new ArrayList<>();
        if (variables != null) {
            for (RuleVariable variable : variables) {
                String scriptName = resolveScriptName(variable);
                if (hasText(scriptName)) {
                    names.add(scriptName);
                }
            }
        }
        if (models != null) {
            for (RuleModel model : models) {
                String modelCode = trimToNull(model.getModelCode());
                if (modelCode != null) {
                    names.add(modelCode);
                }
            }
        }
        return names.toString();
    }

    private boolean isModelRequired(String modelCode, Set<String> requiredScriptNames) {
        for (String required : requiredScriptNames) {
            if (modelCode.equals(required) || (required != null && required.startsWith(modelCode + "."))) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldRefreshModel(String modelCode, Map<String, Object> resolvedParams, VariableResolveOptions options) {
        if (options != null && options.isForceRefreshSource()) {
            return true;
        }
        return !resolvedParams.containsKey(modelCode);
    }

    private boolean modelSucceeded(Map<String, Object> result) {
        Object success = result == null ? null : result.get("success");
        return Boolean.TRUE.equals(success) || (success instanceof Number && ((Number) success).intValue() == 1);
    }

    private String modelError(Map<String, Object> result) {
        if (result == null) {
            return "返回为空";
        }
        Object error = firstNonNull(result.get("error"), result.get("message"));
        return error == null ? "未知错误" : String.valueOf(error);
    }

    Map<String, Object> buildModelParams(RuleModel model, Map<String, Object> resolvedParams) {
        Map<String, Object> params = resolvedParams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(resolvedParams);
        List<RuleModelInputField> fields = model == null ? null : model.getInputFields();
        if (fields == null || fields.isEmpty()) {
            return params;
        }
        for (RuleModelInputField field : fields) {
            String fieldName = firstText(field.getFieldName(), field.getScriptName());
            String scriptName = firstText(field.getScriptName(), field.getFieldName());
            if (fieldName == null || scriptName == null) {
                continue;
            }
            Object value = OperandValueResolver.resolve(field.getSourceOperand(), resolvedParams);
            if (value == null) value = readPath(resolvedParams, scriptName);
            if (value == null && !fieldName.equals(scriptName)) {
                value = readPath(resolvedParams, fieldName);
            }
            String modelCode = model == null ? null : trimToNull(model.getModelCode());
            if (value == null && modelCode != null) {
                value = readPath(resolvedParams, modelCode + "_fields." + scriptName);
            }
            if (value == null && modelCode != null && !fieldName.equals(scriptName)) {
                value = readPath(resolvedParams, modelCode + "_fields." + fieldName);
            }
            if (value == null) {
                value = findUniqueNestedFieldValue(resolvedParams, fieldName);
            }
            if (value == null) {
                value = OperandValueResolver.resolve(field.getDefaultOperand(), resolvedParams);
            }
            if (value == null && hasText(field.getDefaultValue())) {
                value = parseJsonOrRaw(field.getDefaultValue());
            }
            params.put(fieldName, value);
        }
        return params;
    }

    private Object findUniqueNestedFieldValue(Map<String, Object> params, String fieldName) {
        if (params == null || !hasText(fieldName)) {
            return null;
        }
        Object matched = null;
        boolean found = false;
        for (Object value : params.values()) {
            if (!(value instanceof Map)) {
                continue;
            }
            Map<?, ?> nested = (Map<?, ?>) value;
            if (!nested.containsKey(fieldName)) {
                continue;
            }
            if (found) {
                return null;
            }
            matched = nested.get(fieldName);
            found = true;
        }
        return matched;
    }

    private RuleModel loadModelDetail(RuleModel model) {
        if (ruleModelService == null || model == null || model.getId() == null) {
            return model;
        }
        RuleModel detail = ruleModelService.getDetail(model.getId());
        return detail == null ? model : detail;
    }

    private List<RuleModel> loadProjectModels(Long projectId) {
        if (ruleModelService == null) {
            return Collections.emptyList();
        }
        try {
            List<RuleModel> models = ruleModelService.listByProject(projectId);
            return models == null ? Collections.emptyList() : models;
        } catch (Exception e) {
            return Collections.emptyList();
        }
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
        List<Object> queryParams = alignQueryParamsWithSql(sql, buildParamList(config.get("params"), params));
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

    private List<Object> alignQueryParamsWithSql(String sql, List<Object> queryParams) {
        int placeholderCount = countJdbcPlaceholders(sql);
        if (placeholderCount < 0 || queryParams == null || queryParams.size() <= placeholderCount) {
            return queryParams;
        }
        return new ArrayList<>(queryParams.subList(0, placeholderCount));
    }

    private int countJdbcPlaceholders(String sql) {
        if (!hasText(sql)) {
            return -1;
        }
        int count = 0;
        boolean inSingleQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                inSingleQuote = !inSingleQuote;
            } else if (c == '?' && !inSingleQuote) {
                count++;
            }
        }
        return count;
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
            String path = trimToNull(text);
            if (isReferencePathText(path) && containsPath(params, path)) {
                return readPath(params, path);
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

    private boolean containsPath(Object root, String path) {
        if (root == null || !hasText(path)) {
            return false;
        }
        String normalized = path.startsWith("$.") ? path.substring(2) : path;
        Object current = root;
        String[] parts = normalized.split("\\.");
        for (String part : parts) {
            if (current instanceof JSONObject) {
                JSONObject object = (JSONObject) current;
                if (!object.containsKey(part)) {
                    return false;
                }
                current = object.get(part);
            } else if (current instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) current;
                if (!map.containsKey(part)) {
                    return false;
                }
                current = map.get(part);
            } else if (current instanceof List) {
                Integer index = parseIndex(part);
                if (index == null || index < 0 || index >= ((List<?>) current).size()) {
                    return false;
                }
                current = ((List<?>) current).get(index);
            } else if (current != null && current.getClass().isArray()) {
                Integer index = parseIndex(part);
                if (index == null || index < 0 || index >= Array.getLength(current)) {
                    return false;
                }
                current = Array.get(current, index);
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean isReferencePathText(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
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

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String text = trimToNull(value);
            if (text != null) {
                return text;
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
