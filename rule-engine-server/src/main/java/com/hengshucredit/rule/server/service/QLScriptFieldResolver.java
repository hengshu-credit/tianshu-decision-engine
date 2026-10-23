package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.script.QLScriptAnalysis;
import com.hengshucredit.rule.core.script.QLScriptAnalyzer;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

@Service
public class QLScriptFieldResolver {

    private final RuleVariableMapper ruleVariableMapper;
    private final RuleDataObjectMapper ruleDataObjectMapper;
    private final RuleDataObjectFieldMapper ruleDataObjectFieldMapper;
    private final RuleModelMapper ruleModelMapper;
    private final RuleModelOutputFieldMapper ruleModelOutputFieldMapper;
    private final QLScriptAnalyzer scriptAnalyzer = new QLScriptAnalyzer();

    public QLScriptFieldResolver(RuleVariableMapper ruleVariableMapper,
                                 RuleDataObjectMapper ruleDataObjectMapper,
                                 RuleDataObjectFieldMapper ruleDataObjectFieldMapper,
                                 RuleModelMapper ruleModelMapper,
                                 RuleModelOutputFieldMapper ruleModelOutputFieldMapper) {
        this.ruleVariableMapper = ruleVariableMapper;
        this.ruleDataObjectMapper = ruleDataObjectMapper;
        this.ruleDataObjectFieldMapper = ruleDataObjectFieldMapper;
        this.ruleModelMapper = ruleModelMapper;
        this.ruleModelOutputFieldMapper = ruleModelOutputFieldMapper;
    }

    public RuleFieldAnalyzer.ResolvedFields resolve(String modelJson, Long projectId) {
        JSONObject model;
        try {
            model = JSON.parseObject(modelJson);
        } catch (RuntimeException error) {
            return emptyWithDiagnostic(RuleValidationIssue.error(
                    "QL_PARSE_ERROR", "$", "QL 脚本模型不是有效 JSON"));
        }
        if (model == null) {
            return emptyWithDiagnostic(RuleValidationIssue.error(
                    "QL_PARSE_ERROR", "$", "QL 脚本模型不能为空"));
        }

        QLScriptAnalysis analysis = scriptAnalyzer.analyze(model.getString("script"));
        List<RuleValidationIssue> diagnostics = new ArrayList<>();
        for (QLScriptAnalysis.Diagnostic diagnostic : analysis.getDiagnostics()) {
            diagnostics.add(new RuleValidationIssue(
                    diagnostic.getSeverity(), diagnostic.getCode(), diagnostic.getPath(),
                    null, null, diagnostic.getMessage()));
        }

        List<ScriptReference> references = validateUsedReferences(
                model.getJSONArray("scriptVarRefs"), analysis, projectId, diagnostics);
        List<RuleDefinitionInputField> inputFields = new ArrayList<>();
        Map<String, Object> inputPropertySchemas = new LinkedHashMap<>();
        int inputOrder = 0;
        for (String inputPath : analysis.getDirectInputs()) {
            ScriptReference binding = findInputBinding(references, inputPath);
            RuleDefinitionInputField field = newInputField(inputPath, inputOrder++);
            if (binding != null) {
                applyReference(field, binding.validated);
                if (binding.validated.getValueType() != null) {
                    inputPropertySchemas.put(inputPath, binding.validated.getValueType());
                }
            } else {
                diagnostics.add(RuleValidationIssue.error(
                                "SCRIPT_INPUT_REF_MISSING",
                                "$.script." + inputPath,
                                "脚本外部输入缺少稳定 ID 绑定")
                        .withSafeDetail("fieldPath", inputPath));
            }
            inputFields.add(field);
        }

        List<RuleDefinitionOutputField> outputFields = new ArrayList<>();
        Set<String> localOutputNames = new LinkedHashSet<>();
        Map<String, Object> outputPropertySchemas = new LinkedHashMap<>();
        int outputOrder = 0;
        for (QLScriptAnalysis.OutputField output : analysis.getPublicOutputs()) {
            String outputName = output.getName();
            RuleDefinitionOutputField field = newOutputField(outputName, output.getValueType(), outputOrder++);
            ScriptReference binding = findExactBinding(references, outputName);
            if (binding != null) {
                applyReference(field, binding.validated);
            } else {
                localOutputNames.add(outputName);
            }
            String valueType = binding != null ? binding.validated.getValueType() : output.getValueType();
            if (valueType != null) {
                outputPropertySchemas.put(outputName, valueType);
            }
            outputFields.add(field);
        }

        return new RuleFieldAnalyzer.ResolvedFields(
                inputFields, outputFields, diagnostics, localOutputNames,
                inputPropertySchemas, outputPropertySchemas);
    }

    public ValidatedScriptReference validateReference(Long projectId, Long refId, String refType) {
        return validateReferenceResult(projectId, refId, refType).reference;
    }

    private List<ScriptReference> validateUsedReferences(JSONArray refs, QLScriptAnalysis analysis,
                                                          Long projectId,
                                                          List<RuleValidationIssue> diagnostics) {
        if (refs == null || refs.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Map<String, ParsedReference>> grouped = new TreeMap<>();
        List<ScriptReference> result = new ArrayList<>();
        for (int index = 0; index < refs.size(); index++) {
            Object value = refs.get(index);
            String path = "$.scriptVarRefs[" + index + "]";
            if (!(value instanceof JSONObject)) {
                diagnostics.add(referenceIssue(
                        "SCRIPT_REFERENCE_INVALID", path,
                        "脚本稳定引用必须是对象", null, null, null));
                continue;
            }
            JSONObject ref = (JSONObject) value;
            String refCode = trimToNull(ref.getString("refCode"));
            Object rawRefId = ref.get("varId");
            Long refId = rawRefId instanceof Number ? ((Number) rawRefId).longValue() : null;
            String refType = normalizeRefType(ref.getString("refType"));
            if (refCode == null || refId == null || refType == null) {
                diagnostics.add(referenceIssue(
                        "SCRIPT_REFERENCE_INVALID", path,
                        "脚本稳定引用缺少 varId、refType 或 refCode",
                        refType, refId, refCode));
                continue;
            }
            String identity = refType + ":" + refId;
            grouped.computeIfAbsent(refCode, key -> new LinkedHashMap<>())
                    .putIfAbsent(identity, new ParsedReference(refId, refType));
        }
        for (Map.Entry<String, Map<String, ParsedReference>> entry : grouped.entrySet()) {
            String refCode = entry.getKey();
            if (!isUsed(refCode, analysis)) {
                continue;
            }
            Map<String, ParsedReference> identities = entry.getValue();
            String path = "$.script." + refCode;
            if (identities.size() > 1) {
                diagnostics.add(referenceIssue(
                        "SCRIPT_REFERENCE_CONFLICT", path,
                        "同一脚本根绑定了多个不同的稳定引用",
                        null, null, refCode));
                continue;
            }
            ParsedReference ref = identities.values().iterator().next();
            ValidationResult validation = validateReferenceResult(projectId, ref.refId, ref.refType);
            if (validation.reference == null) {
                diagnostics.add(referenceIssue(validation.code, path, validation.message,
                        ref.refType, ref.refId, refCode));
                continue;
            }
            if (!refCode.equals(validation.reference.getScriptRoot())) {
                diagnostics.add(referenceIssue(
                        "REFERENCE_TYPE_MISMATCH", path,
                        "refCode 与稳定 ID 对应的脚本根不一致",
                        ref.refType, ref.refId, refCode));
                continue;
            }
            result.add(new ScriptReference(refCode, validation.reference));
        }
        return result;
    }

    private ValidationResult validateReferenceResult(Long projectId, Long refId, String rawRefType) {
        String refType = normalizeRefType(rawRefType);
        if (refId == null) {
            return ValidationResult.invalid("REFERENCE_NOT_FOUND", "稳定引用 ID 不存在");
        }
        if (refType == null) {
            return ValidationResult.invalid(
                    "REFERENCE_TYPE_MISMATCH", "稳定引用缺少资源类型");
        }
        switch (refType) {
            case "VARIABLE":
            case "CONSTANT":
                return validateVariable(projectId, refId, refType);
            case "DATA_OBJECT":
                return validateDataObjectField(projectId, refId);
            case "DATA_OBJECT_ROOT":
                return validateDataObjectRoot(projectId, refId);
            case "MODEL":
                return validateModel(projectId, refId);
            case "MODEL_OUTPUT":
                return validateModelOutput(projectId, refId);
            default:
                return ValidationResult.invalid(
                        "REFERENCE_TYPE_MISMATCH", "不支持的稳定引用类型");
        }
    }

    private ValidationResult validateVariable(Long projectId, Long refId, String refType) {
        RuleVariable variable = ruleVariableMapper.selectById(refId);
        if (variable == null) {
            return ValidationResult.invalid("REFERENCE_NOT_FOUND", "变量或常量引用不存在");
        }
        String actualType = "CONSTANT".equals(normalizeRefType(variable.getVarSource()))
                ? "CONSTANT" : "VARIABLE";
        if (!refType.equals(actualType) || !active(variable.getStatus())
                || !available(variable.getScope(), variable.getProjectId(), projectId)) {
            return ValidationResult.invalid(
                    "REFERENCE_TYPE_MISMATCH", "变量或常量引用类型、状态或项目不匹配");
        }
        String scriptRoot = firstNonBlank(variable.getScriptName(), variable.getVarCode());
        if (scriptRoot == null) {
            return ValidationResult.invalid(
                    "REFERENCE_TYPE_MISMATCH", "变量或常量没有可用的脚本根");
        }
        return ValidationResult.valid(new ValidatedScriptReference(
                refId, refType, scriptRoot, variable.getVarType()));
    }

    private ValidationResult validateDataObjectRoot(Long projectId, Long refId) {
        RuleDataObject object = ruleDataObjectMapper.selectById(refId);
        if (object == null) return ValidationResult.invalid("REFERENCE_NOT_FOUND", "数据对象不存在");
        if (!active(object.getStatus()) || !available(object.getScope(), object.getProjectId(), projectId))
            return ValidationResult.invalid("REFERENCE_TYPE_MISMATCH", "数据对象状态或项目不匹配");
        String root = firstNonBlank(object.getScriptName(), object.getObjectCode());
        if (root == null) return ValidationResult.invalid("REFERENCE_TYPE_MISMATCH", "数据对象没有可用的脚本根");
        return ValidationResult.valid(new ValidatedScriptReference(refId, "DATA_OBJECT_ROOT", root, "OBJECT"));
    }

    private ValidationResult validateDataObjectField(Long projectId, Long refId) {
        RuleDataObjectField leaf = ruleDataObjectFieldMapper.selectById(refId);
        if (leaf == null) {
            return ValidationResult.invalid("REFERENCE_NOT_FOUND", "数据对象字段引用不存在");
        }
        if (leaf.getObjectId() == null) {
            return ValidationResult.invalid(
                    "REFERENCE_TYPE_MISMATCH", "数据对象字段没有稳定的所属对象 ID");
        }
        RuleDataObject object = ruleDataObjectMapper.selectById(leaf.getObjectId());
        if (object == null) {
            return ValidationResult.invalid("REFERENCE_NOT_FOUND", "数据对象字段所属对象不存在");
        }
        if (!active(object.getStatus())
                || !available(object.getScope(), object.getProjectId(), projectId)) {
            return ValidationResult.invalid(
                    "REFERENCE_TYPE_MISMATCH", "数据对象状态或项目不匹配");
        }
        String objectRoot = firstNonBlank(object.getScriptName(), object.getObjectCode());
        if (objectRoot == null) {
            return ValidationResult.invalid(
                    "REFERENCE_TYPE_MISMATCH", "数据对象没有可用的脚本根");
        }
        List<RuleDataObjectField> chain = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        RuleDataObjectField current = leaf;
        while (current != null) {
            if (current.getId() == null || !visited.add(current.getId())
                    || !active(current.getStatus())
                    || !available(current.getScope(), current.getProjectId(), projectId)
                    || !Objects.equals(leaf.getObjectId(), current.getObjectId())) {
                return ValidationResult.invalid(
                        "REFERENCE_TYPE_MISMATCH", "数据对象字段父子链、状态或项目不匹配");
            }
            chain.add(current);
            Long parentId = current.getParentFieldId();
            if (parentId == null) {
                break;
            }
            current = ruleDataObjectFieldMapper.selectById(parentId);
            if (current == null) {
                return ValidationResult.invalid(
                        "REFERENCE_NOT_FOUND", "数据对象字段的父字段不存在");
            }
        }
        Collections.reverse(chain);
        String relativePath = null;
        for (RuleDataObjectField field : chain) {
            String fieldPath = firstNonBlank(field.getScriptName(), field.getVarCode());
            if (fieldPath == null) {
                return ValidationResult.invalid(
                        "REFERENCE_TYPE_MISMATCH", "数据对象字段没有可用的脚本路径");
            }
            if (relativePath == null || fieldPath.equals(relativePath)
                    || fieldPath.startsWith(relativePath + ".")) {
                relativePath = fieldPath;
            } else {
                relativePath = relativePath + "." + fieldPath;
            }
        }
        String scriptRoot = relativePath.equals(objectRoot)
                || relativePath.startsWith(objectRoot + ".")
                ? relativePath : objectRoot + "." + relativePath;
        return ValidationResult.valid(new ValidatedScriptReference(
                refId, "DATA_OBJECT", scriptRoot, leaf.getVarType()));
    }

    private ValidationResult validateModel(Long projectId, Long refId) {
        RuleModel model = ruleModelMapper.selectById(refId);
        if (model == null) {
            return ValidationResult.invalid("REFERENCE_NOT_FOUND", "模型引用不存在");
        }
        if (!active(model.getStatus())
                || !available(model.getScope(), model.getProjectId(), projectId)) {
            return ValidationResult.invalid(
                    "REFERENCE_TYPE_MISMATCH", "模型状态或项目不匹配");
        }
        String scriptRoot = trimToNull(model.getModelCode());
        if (scriptRoot == null) {
            return ValidationResult.invalid(
                    "REFERENCE_TYPE_MISMATCH", "模型没有可用的脚本根");
        }
        return ValidationResult.valid(new ValidatedScriptReference(
                refId, "MODEL", scriptRoot, "MODEL"));
    }

    private ValidationResult validateModelOutput(Long projectId, Long refId) {
        RuleModelOutputField output = ruleModelOutputFieldMapper.selectById(refId);
        if (output == null || output.getModelId() == null) {
            return ValidationResult.invalid("REFERENCE_NOT_FOUND", "模型输出字段引用不存在");
        }
        RuleModel model = ruleModelMapper.selectById(output.getModelId());
        if (model == null) {
            return ValidationResult.invalid("REFERENCE_NOT_FOUND", "模型输出字段所属模型不存在");
        }
        if (!active(model.getStatus())
                || !available(model.getScope(), model.getProjectId(), projectId)) {
            return ValidationResult.invalid(
                    "REFERENCE_TYPE_MISMATCH", "模型输出字段所属模型状态或项目不匹配");
        }
        String modelRoot = trimToNull(model.getModelCode());
        String outputRoot = firstNonBlank(
                output.getFieldName(), output.getFeatureName(), output.getScriptName());
        if (modelRoot == null || outputRoot == null) {
            return ValidationResult.invalid(
                    "REFERENCE_TYPE_MISMATCH", "模型输出字段没有可用的脚本根");
        }
        return ValidationResult.valid(new ValidatedScriptReference(
                refId, "MODEL_OUTPUT", modelRoot + "." + outputRoot, output.getFieldType()));
    }

    private boolean isUsed(String refCode, QLScriptAnalysis analysis) {
        for (String input : analysis.getDirectInputs()) {
            if (input.equals(refCode) || input.startsWith(refCode + ".")) {
                return true;
            }
        }
        for (QLScriptAnalysis.OutputField output : analysis.getPublicOutputs()) {
            if (output.getName().equals(refCode)
                    || output.getName().startsWith(refCode + ".")) {
                return true;
            }
        }
        return false;
    }

    private ScriptReference findInputBinding(List<ScriptReference> references, String inputPath) {
        ScriptReference longestPrefix = null;
        for (ScriptReference reference : references) {
            String refType = reference.validated.getRefType();
            if (inputPath.equals(reference.refCode)) {
                return reference;
            }
            if (("VARIABLE".equals(refType) || "DATA_OBJECT".equals(refType))
                    && inputPath.startsWith(reference.refCode + ".")
                    && (longestPrefix == null
                    || reference.refCode.length() > longestPrefix.refCode.length())) {
                longestPrefix = reference;
            }
        }
        return longestPrefix;
    }

    private ScriptReference findExactBinding(List<ScriptReference> references, String path) {
        for (ScriptReference reference : references) {
            if (path.equals(reference.refCode)) {
                return reference;
            }
        }
        return null;
    }

    private RuleDefinitionInputField newInputField(String path, int order) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setFieldName(path);
        field.setFieldLabel(path);
        field.setScriptName(path);
        field.setFieldType("STRING");
        field.setSortOrder(order);
        field.setStatus(1);
        field.setCreateTime(LocalDateTime.now());
        return field;
    }

    private RuleDefinitionOutputField newOutputField(String name, String valueType, int order) {
        RuleDefinitionOutputField field = new RuleDefinitionOutputField();
        field.setFieldName(name);
        field.setFieldLabel(name);
        field.setScriptName(name);
        field.setFieldType(firstNonBlank(valueType, "STRING"));
        field.setSortOrder(order);
        field.setStatus(1);
        field.setCreateTime(LocalDateTime.now());
        return field;
    }

    private void applyReference(RuleDefinitionInputField field,
                                ValidatedScriptReference reference) {
        field.setVarId(reference.getReferenceId());
        field.setRefType(reference.getRefType());
        if (reference.getValueType() != null) {
            field.setFieldType(reference.getValueType());
        }
    }

    private void applyReference(RuleDefinitionOutputField field,
                                ValidatedScriptReference reference) {
        field.setVarId(reference.getReferenceId());
        field.setRefType(reference.getRefType());
        if (reference.getValueType() != null) {
            field.setFieldType(reference.getValueType());
        }
    }

    private RuleValidationIssue referenceIssue(String code, String path, String message,
                                               String refType, Long refId, String fieldPath) {
        RuleValidationIssue issue = RuleValidationIssue.error(code, path, message)
                .withReference(refType, refId);
        if (fieldPath != null) {
            issue.withSafeDetail("fieldPath", fieldPath);
        }
        return issue;
    }

    private RuleFieldAnalyzer.ResolvedFields emptyWithDiagnostic(RuleValidationIssue diagnostic) {
        return new RuleFieldAnalyzer.ResolvedFields(
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(diagnostic), Collections.emptySet(),
                Collections.emptyMap(), Collections.emptyMap());
    }

    private boolean active(Integer status) {
        return Integer.valueOf(1).equals(status);
    }

    private boolean available(String scope, Long resourceProjectId, Long projectId) {
        return RuleVariableService.SCOPE_GLOBAL.equalsIgnoreCase(trimToEmpty(scope))
                || projectId == null
                || Objects.equals(resourceProjectId, projectId);
    }

    private String normalizeRefType(String value) {
        String type = trimToNull(value);
        return type == null ? null : type.toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "" : trimmed;
    }

    public static final class ValidatedScriptReference {
        private final Long referenceId;
        private final String refType;
        private final String scriptRoot;
        private final String valueType;

        public ValidatedScriptReference(Long referenceId, String refType,
                                        String scriptRoot, String valueType) {
            this.referenceId = referenceId;
            this.refType = refType;
            this.scriptRoot = scriptRoot;
            this.valueType = valueType;
        }

        public Long getReferenceId() {
            return referenceId;
        }

        public String getRefType() {
            return refType;
        }

        public String getScriptRoot() {
            return scriptRoot;
        }

        public String getValueType() {
            return valueType;
        }
    }

    private static final class ScriptReference {
        private final String refCode;
        private final ValidatedScriptReference validated;

        private ScriptReference(String refCode, ValidatedScriptReference validated) {
            this.refCode = refCode;
            this.validated = validated;
        }
    }

    private static final class ParsedReference {
        private final Long refId;
        private final String refType;

        private ParsedReference(Long refId, String refType) {
            this.refId = refId;
            this.refType = refType;
        }
    }

    private static final class ValidationResult {
        private final ValidatedScriptReference reference;
        private final String code;
        private final String message;

        private ValidationResult(ValidatedScriptReference reference, String code, String message) {
            this.reference = reference;
            this.code = code;
            this.message = message;
        }

        private static ValidationResult valid(ValidatedScriptReference reference) {
            return new ValidationResult(reference, null, null);
        }

        private static ValidationResult invalid(String code, String message) {
            return new ValidationResult(null, code, message);
        }
    }
}
