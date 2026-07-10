package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ResolutionPlan;
import com.hengshucredit.rule.model.dto.ResolvedField;
import com.hengshucredit.rule.model.dto.RuleTestSchemaRequest;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 规则、模型和变量共用的字段依赖解析入口。
 * 具体依赖展开委托给 RuleFieldAnalyzer，避免测试、持久化和执行维护平行规则。
 */
@Service
public class FieldDependencyResolver {

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleModelService modelService;

    @Resource
    private RuleVariableService variableService;

    @Resource
    private RuleFieldAnalyzer ruleFieldAnalyzer;

    @Resource
    private RuleExperimentService experimentService;

    public ResolutionPlan resolve(RuleTestSchemaRequest request) {
        if (request == null || !hasText(request.getTargetType())) {
            throw new IllegalArgumentException("targetType不能为空");
        }
        String targetType = request.getTargetType().trim().toUpperCase(Locale.ROOT);
        if ("RULE".equals(targetType)) {
            return resolveRule(request);
        }
        if ("MODEL".equals(targetType)) {
            return resolveModel(request);
        }
        if ("VARIABLE".equals(targetType)) {
            return resolveVariable(request);
        }
        if ("EXPERIMENT".equals(targetType)) {
            return resolveExperiment(request);
        }
        throw new IllegalArgumentException("不支持的targetType: " + request.getTargetType());
    }

    private ResolutionPlan resolveRule(RuleTestSchemaRequest request) {
        Long definitionId = request.getTargetId();
        List<RuleDefinitionInputField> inputs;
        List<RuleDefinitionOutputField> outputs;
        if (hasText(request.getModelJson())) {
            Long projectId = request.getProjectId();
            String modelType = request.getModelType();
            if (definitionId != null) {
                RuleDefinition definition = definitionService.getById(definitionId);
                if (definition != null) {
                    if (projectId == null) projectId = definition.getProjectId();
                    if (!hasText(modelType)) modelType = definition.getModelType();
                }
            }
            RuleFieldAnalyzer.ResolvedFields fields = ruleFieldAnalyzer.resolveFields(
                    definitionId, request.getModelJson(), modelType, projectId);
            inputs = fields.getInputFields();
            outputs = fields.getOutputFields();
        } else {
            requireTargetId(definitionId);
            RuleDefinition definition = definitionService.getById(definitionId);
            RuleDefinitionContent content = definitionService.getContent(definitionId);
            if (definition != null && content != null && hasText(content.getModelJson())) {
                RuleFieldAnalyzer.ResolvedFields fields = ruleFieldAnalyzer.resolveFields(
                        definitionId, content.getModelJson(), definition.getModelType(), definition.getProjectId());
                inputs = fields.getInputFields();
                outputs = fields.getOutputFields();
            } else {
                inputs = definitionService.listInputFields(definitionId);
                outputs = definitionService.listOutputFields(definitionId);
            }
        }
        return planFromRuleFields(inputs, outputs);
    }

    private ResolutionPlan resolveModel(RuleTestSchemaRequest request) {
        Long modelId = request.getTargetId();
        requireTargetId(modelId);
        RuleModel model = modelService.getDetail(modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在: " + modelId);
        }
        List<RuleDefinitionInputField> rawInputs = new ArrayList<>();
        for (RuleModelInputField field : safe(modelService.listInputFields(modelId))) {
            rawInputs.add(copyModelInput(field));
        }
        List<RuleDefinitionInputField> inputs = ruleFieldAnalyzer.resolveInputFields(rawInputs, model.getProjectId());
        ResolutionPlan plan = new ResolutionPlan();
        plan.setExternalInputs(toResolvedInputs(inputs));
        List<ResolvedField> outputs = new ArrayList<>();
        for (RuleModelOutputField field : safe(modelService.listOutputFields(modelId))) {
            outputs.add(toResolved(field));
        }
        plan.setOutputs(outputs);
        return plan;
    }

    private ResolutionPlan resolveVariable(RuleTestSchemaRequest request) {
        Long variableId = request.getTargetId();
        requireTargetId(variableId);
        RuleVariable variable = variableService.getById(variableId);
        if (variable == null) {
            throw new IllegalArgumentException("变量不存在: " + variableId);
        }
        RuleDefinitionInputField root = new RuleDefinitionInputField();
        root.setVarId(variable.getId());
        root.setRefType("CONSTANT".equals(variable.getVarSource()) ? "CONSTANT" : "VARIABLE");
        root.setFieldName(variable.getVarCode());
        root.setFieldLabel(variable.getVarLabel());
        root.setScriptName(firstText(variable.getScriptName(), variable.getVarCode()));
        root.setFieldType(variable.getVarType());
        root.setDefaultValue(variable.getDefaultValue());
        root.setStatus(1);

        ResolutionPlan plan = new ResolutionPlan();
        plan.setExternalInputs(toResolvedInputs(ruleFieldAnalyzer.resolveInputFields(
                Collections.singletonList(root), variable.getProjectId())));
        ResolvedField variableField = toResolved(root);
        variableField.setSourceType(variable.getVarSource());
        plan.setOutputs(Collections.singletonList(variableField));
        if (!"INPUT".equals(variable.getVarSource()) && !"CONSTANT".equals(variable.getVarSource())) {
            plan.setRuntimeNodes(Collections.singletonList(variableField));
        }
        return plan;
    }

    private ResolutionPlan resolveExperiment(RuleTestSchemaRequest request) {
        Long experimentId = request.getTargetId();
        requireTargetId(experimentId);
        RuleFieldAnalyzer.ResolvedFields fields = experimentService.resolveTestFields(experimentId);
        return planFromRuleFields(fields.getInputFields(), fields.getOutputFields());
    }

    private ResolutionPlan planFromRuleFields(List<RuleDefinitionInputField> inputs,
                                               List<RuleDefinitionOutputField> outputs) {
        ResolutionPlan plan = new ResolutionPlan();
        plan.setExternalInputs(toResolvedInputs(inputs));
        List<ResolvedField> resolvedOutputs = new ArrayList<>();
        for (RuleDefinitionOutputField field : safe(outputs)) {
            resolvedOutputs.add(toResolved(field));
        }
        plan.setOutputs(resolvedOutputs);
        return plan;
    }

    private List<ResolvedField> toResolvedInputs(List<RuleDefinitionInputField> inputs) {
        Map<String, ResolvedField> dedup = new LinkedHashMap<>();
        for (RuleDefinitionInputField field : safe(inputs)) {
            ResolvedField resolved = toResolved(field);
            String key = (resolved.getRefType() == null ? "" : resolved.getRefType()) + ":"
                    + (resolved.getRefId() == null ? resolved.getScriptName() : resolved.getRefId());
            dedup.putIfAbsent(key, resolved);
        }
        return new ArrayList<>(dedup.values());
    }

    private ResolvedField toResolved(RuleDefinitionInputField field) {
        ResolvedField resolved = baseResolved(field.getVarId(), field.getRefType(), field.getFieldName(),
                field.getFieldLabel(), field.getScriptName(), field.getFieldType());
        resolved.setDefaultValue(field.getDefaultValue());
        resolved.setValidValues(field.getValidValues());
        resolved.setSourceType(sourceType(field.getRefType()));
        return resolved;
    }

    private ResolvedField toResolved(RuleDefinitionOutputField field) {
        ResolvedField resolved = baseResolved(field.getVarId(), field.getRefType(), field.getFieldName(),
                field.getFieldLabel(), field.getScriptName(), field.getFieldType());
        resolved.setValidValues(field.getValidValues());
        resolved.setSourceType(sourceType(field.getRefType()));
        return resolved;
    }

    private ResolvedField toResolved(RuleModelOutputField field) {
        ResolvedField resolved = baseResolved(field.getVarId(), field.getRefType(), field.getFieldName(),
                field.getFieldLabel(), field.getScriptName(), field.getFieldType());
        resolved.setSourceType("MODEL_OUTPUT");
        return resolved;
    }

    private ResolvedField baseResolved(Long refId, String refType, String code, String label,
                                       String scriptName, String valueType) {
        ResolvedField field = new ResolvedField();
        field.setRefId(refId);
        field.setRefType(refType);
        field.setCode(firstText(scriptName, code));
        field.setLabel(firstText(label, code, scriptName));
        field.setScriptName(firstText(scriptName, code));
        field.setValueType(valueType);
        return field;
    }

    private RuleDefinitionInputField copyModelInput(RuleModelInputField source) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setVarId(source.getVarId());
        field.setRefType(source.getRefType());
        field.setFieldName(source.getFieldName());
        field.setFieldLabel(source.getFieldLabel());
        field.setScriptName(source.getScriptName());
        field.setFieldType(source.getFieldType());
        field.setDefaultValue(source.getDefaultValue());
        field.setValidValues(source.getValidValues());
        field.setStatus(source.getStatus());
        return field;
    }

    private String sourceType(String refType) {
        if ("DATA_OBJECT".equals(refType)) return "DATA_OBJECT";
        if ("CONSTANT".equals(refType)) return "CONSTANT";
        if ("MODEL".equals(refType) || "MODEL_OUTPUT".equals(refType)) return "MODEL";
        return "INPUT";
    }

    private void requireTargetId(Long targetId) {
        if (targetId == null) throw new IllegalArgumentException("targetId不能为空");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return null;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
