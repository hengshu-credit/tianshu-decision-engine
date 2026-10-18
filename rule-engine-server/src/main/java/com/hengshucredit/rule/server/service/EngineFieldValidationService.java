package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared field-validation bindings used by rule, experiment and external API
 * input boundaries. Binding identity is refType:refId when available and
 * PATH:path for path-only inputs.
 */
@Service
public class EngineFieldValidationService {

    @Resource
    private RuleFieldValidationService validationService;

    public void validateApiConfiguration(Long projectId,
                                         RuleExternalApiConfig api) {
        JSONObject specification = ExternalApiRequestPlan.specification(api);
        if (specification == null) return;
        validateFieldList(projectId, specification.get("requestFields"),
                "$.executionConfig.requestFields");
        validateBranchFields(projectId, specification.get("requestBranches"),
                "$.executionConfig.requestBranches");
        validateStepFields(projectId, specification.get("steps"),
                "$.executionConfig.steps");
    }

    public void validateApiPlan(Long projectId,
                                ExternalApiRequestPlan plan) {
        if (plan == null) return;
        JSONObject specification = ExternalApiRequestPlan.specification(
                plan.config());
        if (specification == null) return;
        List<RuleDefinitionInputField> fields = new ArrayList<>();
        for (JSONObject field : ExternalApiRequestPlan.fields(specification)) {
            List<Long> ids = ruleIds(field.get("validationRuleIds"));
            if (ids.isEmpty()) continue;
            RuleDefinitionInputField input = new RuleDefinitionInputField();
            String id = field.getString("id");
            input.setFieldName(id);
            input.setFieldLabel(field.getString("path"));
            input.setScriptName(id);
            input.setFieldType("OBJECT");
            input.setStatus(1);
            input.setValidationRuleIds(validationService.validateRuleIds(
                    projectId, JSON.toJSONString(ids)));
            fields.add(input);
        }
        if (fields.isEmpty()) return;
        Object rawValues = plan.params().get("__apiFields");
        Map<String, Object> apiValues = rawValues instanceof Map<?, ?> map
                ? copyMap(map) : Collections.emptyMap();
        Map<String, Object> values = new LinkedHashMap<>();
        for (RuleDefinitionInputField field : fields) {
            String id = field.getFieldName();
            String path = field.getFieldLabel();
            values.put(path, apiValues.get(id));
            field.setFieldName(path);
            field.setScriptName(path);
        }
        validationService.validateInputFields(fields, values);
    }

    public void validateExperimentConfiguration(Long projectId,
                                                String configuration) {
        Map<String, Object> bindings = parseBindings(configuration);
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("实验输入字段校验绑定键不能为空");
            }
            validationService.validateRuleIds(projectId,
                    JSON.toJSONString(ruleIds(entry.getValue())));
        }
    }

    public void validateExperimentInputs(Long projectId,
                                         String configuration,
                                         List<RuleDefinitionInputField> fields,
                                         Map<String, Object> params) {
        Map<String, Object> bindings = parseBindings(configuration);
        if (bindings.isEmpty() || fields == null || fields.isEmpty()) return;
        List<RuleDefinitionInputField> effective = new ArrayList<>();
        for (RuleDefinitionInputField field : fields) {
            if (field == null) continue;
            RuleDefinitionInputField copy = JSON.parseObject(
                    JSON.toJSONString(field), RuleDefinitionInputField.class);
            LinkedHashSet<Long> ids = new LinkedHashSet<>(
                    ruleIds(field.getValidationRuleIds()));
            Object configured = bindings.get(fieldKey(field));
            if (configured != null) ids.addAll(ruleIds(configured));
            copy.setValidationRuleIds(JSON.toJSONString(ids));
            effective.add(copy);
        }
        validationService.validateInputFields(effective, params);
    }

    public String normalizeExperimentConfiguration(Long projectId,
                                                    String configuration) {
        Map<String, Object> bindings = parseBindings(configuration);
        if (bindings.isEmpty()) return "{}";
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("实验输入字段校验绑定键不能为空");
            }
            String ids = validationService.validateRuleIds(projectId,
                    JSON.toJSONString(ruleIds(entry.getValue())));
            normalized.put(entry.getKey(), JSON.parseArray(ids, Long.class));
        }
        return JSON.toJSONString(normalized);
    }

    public static String fieldKey(RuleDefinitionInputField field) {
        if (field != null && field.getVarId() != null
                && field.getRefType() != null
                && !field.getRefType().isBlank()) {
            return field.getRefType().trim().toUpperCase() + ":"
                    + field.getVarId();
        }
        String path = field == null ? null : firstText(field.getScriptName(),
                field.getFieldName());
        return path == null ? "PATH:" : "PATH:" + path;
    }

    private void validateBranchFields(Long projectId, Object raw,
                                      String path) {
        if (!(raw instanceof Iterable<?> branches)) return;
        int index = 0;
        for (Object branch : branches) {
            JSONObject value = object(branch);
            validateFieldList(projectId, value.get("requestFields"),
                    path + "[" + index + "].requestFields");
            index++;
        }
    }

    private void validateStepFields(Long projectId, Object raw, String path) {
        if (!(raw instanceof Iterable<?> steps)) return;
        int index = 0;
        for (Object step : steps) {
            JSONObject value = object(step);
            validateFieldList(projectId, value.get("requestFields"),
                    path + "[" + index + "].requestFields");
            index++;
        }
    }

    private void validateFieldList(Long projectId, Object raw, String path) {
        if (!(raw instanceof Iterable<?> fields)) return;
        int index = 0;
        for (Object field : fields) {
            JSONObject value = object(field);
            Object ids = value.get("validationRuleIds");
            if (ids != null) {
                try {
                    validationService.validateRuleIds(projectId,
                            JSON.toJSONString(ruleIds(ids)));
                } catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException(error.getMessage()
                            + "（" + path + "[" + index + "]）", error);
                }
            }
            index++;
        }
    }

    private Map<String, Object> parseBindings(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            return Collections.emptyMap();
        }
        Object parsed = JSON.parse(configuration);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("实验输入字段校验配置必须是 JSON 对象");
        }
        return copyMap(map);
    }

    private JSONObject object(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("外数请求字段配置必须是 JSON 对象");
        }
        return JSON.parseObject(JSON.toJSONString(value));
    }

    private List<Long> ruleIds(Object raw) {
        if (raw == null) return List.of();
        try {
            List<Long> parsed = raw instanceof String text
                    ? JSON.parseArray(text, Long.class)
                    : JSON.parseArray(JSON.toJSONString(raw), Long.class);
            return parsed == null ? List.of() : parsed;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("字段校验规则 ID 必须是 JSON 数组", error);
        }
    }

    private Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }
}
