package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.dto.ResolutionPlan;
import com.hengshucredit.rule.model.dto.ResolvedField;
import com.hengshucredit.rule.model.dto.RuleExecuteResult;
import com.hengshucredit.rule.model.dto.RuleExpressionRequest;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleListLibrary;
import com.hengshucredit.rule.model.entity.RuleVariable;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 只针对当前结构化表达式生成测试字段并执行，不读取或执行规则内容。 */
@Service
public class RuleExpressionTestService {

    private static final String MODE_CURRENT = "CURRENT";
    private static final String MODE_DEEP = "DEEP";
    private static final String LIST_QUERY_VALUE = "__expressionListQueryValue";

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleVariableService variableService;

    @Resource
    private RuleFunctionService functionService;

    @Resource
    private RuleListService ruleListService;

    @Resource
    private ListMatchMatrix listMatchMatrix;

    @Resource
    private RuleFieldAnalyzer ruleFieldAnalyzer;

    @Resource
    private RuleTestSchemaService testSchemaService;

    @Resource
    private VariableSourceResolver variableSourceResolver;

    public RuleTestSchema buildSchema(RuleExpressionRequest request) {
        ExpressionContext context = context(request);
        ResolutionPlan plan = MODE_DEEP.equals(context.mode)
                ? deepPlan(context) : currentPlan(context);
        appendListQueryPlan(plan, context);
        return testSchemaService.build(plan);
    }

    public RuleExecuteResult execute(RuleExpressionRequest request) {
        long started = System.currentTimeMillis();
        RuleExecuteResult result = new RuleExecuteResult();
        try {
            ExpressionContext context = context(request);
            Map<String, Object> values = new LinkedHashMap<>();
            if (request.getParams() != null) values.putAll(request.getParams());
            if (MODE_DEEP.equals(context.mode)) {
                VariableResolveOptions options = VariableResolveOptions.defaults();
                options.setForceRefreshSource(true);
                options.setRequiredNamesUpstreamOnly(true);
                options.setRequiredScriptNames(requiredScriptNames(context));
                variableSourceResolver.resolveInto(context.definition.getProjectId(), values, options);
            }
            Object value;
            if ("LIST_QUERY".equals(context.operand.getString("kind"))) {
                value = executeListQuery(context.operand, values);
            } else {
                Map<String, Object> referenceValues = OperandValueResolver.buildReferenceValues(
                        context.referencePaths, values, context.constantValues);
                value = OperandValueResolver.resolve(context.operand, values, referenceValues,
                        (functionId, functionCode, args) -> functionService.invoke(functionId, args));
            }
            result.setSuccess(true);
            result.setResult(value);
        } catch (RuntimeException e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage() == null ? "表达式测试失败" : e.getMessage());
        }
        result.setExecuteTimeMs(System.currentTimeMillis() - started);
        return result;
    }

    private ResolutionPlan currentPlan(ExpressionContext context) {
        ResolutionPlan plan = new ResolutionPlan();
        Map<String, ResolvedField> inputs = new LinkedHashMap<>();
        for (JSONObject reference : context.references) {
            ResolvedField field = directField(reference, context);
            if (field == null || "CONSTANT".equals(field.getRefType())) continue;
            inputs.putIfAbsent(fieldKey(field), field);
        }
        plan.setExternalInputs(new ArrayList<>(inputs.values()));
        return plan;
    }

    private void appendListQueryPlan(ResolutionPlan plan, ExpressionContext context) {
        if (!"LIST_QUERY".equals(context.operand.getString("kind"))) return;
        ResolvedField input = new ResolvedField();
        input.setRefType("PATH");
        input.setCode(LIST_QUERY_VALUE);
        input.setLabel("待匹配值");
        input.setScriptName(LIST_QUERY_VALUE);
        input.setValueType("STRING");
        input.setSourceType("INPUT");
        plan.getExternalInputs().add(input);

        ResolvedField runtimeNode = new ResolvedField();
        JSONArray listIds = context.operand.getJSONArray("listIds");
        runtimeNode.setRefId(listIds == null || listIds.isEmpty() ? null : listIds.getLong(0));
        runtimeNode.setRefType("LIST_QUERY");
        runtimeNode.setCode("listMatch");
        runtimeNode.setLabel("名单匹配");
        runtimeNode.setScriptName("listMatch");
        runtimeNode.setValueType("BOOLEAN");
        runtimeNode.setSourceType("LIST");
        plan.getRuntimeNodes().add(runtimeNode);
        plan.getDiagnostics().add("名单查询测试会真实匹配名单数据");
    }

    private Object executeListQuery(JSONObject operand, Map<String, Object> values) {
        Object queryValue = values.get(LIST_QUERY_VALUE);
        if (queryValue == null || String.valueOf(queryValue).trim().isEmpty()) {
            throw new IllegalArgumentException("名单查询待匹配值不能为空");
        }
        return listMatchMatrix.match(longValues(operand.getJSONArray("listIds")),
                Collections.singletonList(queryValue), operand.getString("combinationMode"),
                operand.getString("matchMode"), stringValues(operand.getJSONArray("itemTypes")), null);
    }

    private ResolutionPlan deepPlan(ExpressionContext context) {
        ResolutionPlan plan = new ResolutionPlan();
        Map<String, ResolvedField> directPaths = new LinkedHashMap<>();
        List<RuleDefinitionInputField> managedInputs = new ArrayList<>();
        Map<String, ResolvedField> runtimeNodes = new LinkedHashMap<>();
        for (JSONObject reference : context.references) {
            ResolvedField field = directField(reference, context);
            if (field == null || "CONSTANT".equals(field.getRefType())) continue;
            if ("PATH".equals(reference.getString("kind"))) {
                directPaths.putIfAbsent(fieldKey(field), field);
                continue;
            }
            managedInputs.add(toInputField(field));
            ResolvedField runtimeNode = runtimeNode(field);
            if (runtimeNode != null) runtimeNodes.putIfAbsent(fieldKey(runtimeNode), runtimeNode);
        }
        Map<String, ResolvedField> externalInputs = new LinkedHashMap<>(directPaths);
        for (RuleDefinitionInputField input : ruleFieldAnalyzer.resolveInputFields(
                managedInputs, context.definition.getProjectId())) {
            ResolvedField field = toResolved(input);
            externalInputs.putIfAbsent(fieldKey(field), field);
        }
        plan.setExternalInputs(new ArrayList<>(externalInputs.values()));
        plan.setRuntimeNodes(new ArrayList<>(runtimeNodes.values()));
        return plan;
    }

    private ResolvedField directField(JSONObject reference, ExpressionContext context) {
        if (reference == null) return null;
        String kind = reference.getString("kind");
        ResolvedField field = new ResolvedField();
        field.setCode(firstText(reference.getString("code"), reference.getString("value")));
        field.setLabel(firstText(reference.getString("label"), field.getCode()));
        field.setValueType(reference.getString("valueType"));
        if ("PATH".equals(kind)) {
            field.setRefType("PATH");
            field.setScriptName(firstText(reference.getString("value"), reference.getString("code")));
            field.setSourceType("INPUT");
            return field;
        }
        Long refId = reference.getLong("refId");
        String refType = upper(reference.getString("refType"));
        if (refId == null || refType == null) {
            throw new IllegalArgumentException("受管字段引用缺少 ID 或引用类型");
        }
        String key = refType + ":" + refId;
        if ("CONSTANT".equals(refType)) {
            if (!context.constantValues.containsKey(key)) {
                throw new IllegalArgumentException("常量引用不存在、已停用或值不合法，ID=" + refId);
            }
        } else if (!context.referencePaths.containsKey(key)) {
            throw new IllegalArgumentException("字段引用不存在或已停用，" + key);
        }
        field.setRefId(refId);
        field.setRefType(refType);
        field.setScriptName("CONSTANT".equals(refType)
                ? field.getCode() : context.referencePaths.get(key));
        field.setSourceType(sourceType(refType));
        return field;
    }

    private ResolvedField runtimeNode(ResolvedField field) {
        String sourceType = null;
        String label = field.getLabel();
        String valueType = field.getValueType();
        if ("VARIABLE".equals(field.getRefType())) {
            RuleVariable variable = variableService.getById(field.getRefId());
            if (variable != null && !"INPUT".equals(variable.getVarSource())
                    && !"CONSTANT".equals(variable.getVarSource())) {
                sourceType = variable.getVarSource();
                label = firstText(variable.getVarLabel(), label);
                valueType = firstText(variable.getVarType(), valueType);
            }
        } else if ("MODEL".equals(field.getRefType()) || "MODEL_OUTPUT".equals(field.getRefType())) {
            sourceType = "MODEL";
        }
        if (sourceType == null) return null;
        ResolvedField node = new ResolvedField();
        node.setRefId(field.getRefId());
        node.setRefType(field.getRefType());
        node.setCode(field.getCode());
        node.setLabel(label);
        node.setScriptName(field.getScriptName());
        node.setValueType(valueType);
        node.setSourceType(sourceType);
        return node;
    }

    private Set<String> requiredScriptNames(ExpressionContext context) {
        Set<String> names = new LinkedHashSet<>();
        for (JSONObject reference : context.references) {
            if (!"REFERENCE".equals(reference.getString("kind"))) continue;
            String refType = upper(reference.getString("refType"));
            Long refId = reference.getLong("refId");
            if (refType == null || refId == null || "CONSTANT".equals(refType)) continue;
            String name = context.referencePaths.get(refType + ":" + refId);
            if (name != null && !name.trim().isEmpty()) names.add(name);
        }
        return names;
    }

    private RuleDefinitionInputField toInputField(ResolvedField source) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setVarId(source.getRefId());
        field.setRefType(source.getRefType());
        field.setFieldName(source.getCode());
        field.setFieldLabel(source.getLabel());
        field.setScriptName(source.getScriptName());
        field.setFieldType(source.getValueType());
        field.setStatus(1);
        return field;
    }

    private ResolvedField toResolved(RuleDefinitionInputField source) {
        ResolvedField field = new ResolvedField();
        field.setRefId(source.getVarId());
        field.setRefType(source.getRefType());
        field.setCode(firstText(source.getFieldName(), source.getScriptName()));
        field.setLabel(firstText(source.getFieldLabel(), source.getFieldName(), source.getScriptName()));
        field.setScriptName(firstText(source.getScriptName(), source.getFieldName()));
        field.setValueType(source.getFieldType());
        field.setDefaultValue(source.getDefaultValue());
        field.setExampleValue(source.getExampleValue());
        field.setValidValues(source.getValidValues());
        field.setSourceType(sourceType(source.getRefType()));
        return field;
    }

    private ExpressionContext context(RuleExpressionRequest request) {
        if (request == null || request.getRuleId() == null) {
            throw new IllegalArgumentException("规则 ID 不能为空");
        }
        RuleDefinition definition = definitionService.getById(request.getRuleId());
        if (definition == null) throw new IllegalArgumentException("规则定义不存在");
        if (request.getOperand() == null || request.getOperand().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        String mode = upper(request.getResolutionMode());
        if (mode == null || mode.isEmpty()) mode = MODE_CURRENT;
        if (!MODE_CURRENT.equals(mode) && !MODE_DEEP.equals(mode)) {
            throw new IllegalArgumentException("不支持的表达式测试模式: " + request.getResolutionMode());
        }
        ExpressionContext context = new ExpressionContext();
        context.definition = definition;
        context.mode = mode;
        context.operand = (JSONObject) JSON.toJSON(request.getOperand());
        context.references = OperandValueResolver.collectReferences(JSON.toJSONString(request.getOperand()));
        context.referencePaths = variableService.buildRefScriptNameMap(definition.getProjectId());
        context.constantValues = variableService.buildRefConstantValueMap(definition.getProjectId());
        context.functionCodes = functionService.buildFunctionCodeMap(definition.getProjectId());
        context.functionArities = functionService.buildFunctionArityMap(definition.getProjectId());
        for (JSONObject reference : context.references) {
            directField(reference, context);
        }
        validateManagedNodes(context.operand, context);
        return context;
    }

    private void validateManagedNodes(JSONObject operand, ExpressionContext context) {
        if (operand == null) return;
        String kind = operand.getString("kind");
        if ("FUNCTION".equals(kind)) {
            Long functionId = operand.getLong("functionId");
            if (functionId == null) throw new IllegalArgumentException("受管方法引用缺少 ID");
            String functionCode = context.functionCodes.get(functionId);
            if (functionCode == null || functionCode.trim().isEmpty()) {
                throw new IllegalArgumentException("方法引用不存在或已停用，ID=" + functionId);
            }
            JSONArray args = operand.getJSONArray("args");
            int actualArity = args == null ? 0 : args.size();
            Integer expectedArity = context.functionArities.get(functionId);
            if (expectedArity != null && expectedArity != actualArity) {
                throw new IllegalArgumentException("方法 " + functionCode + " 需要 " + expectedArity
                        + " 个参数，实际为 " + actualArity);
            }
            validateChildren(args, context);
            return;
        }
        if ("LIST_QUERY".equals(kind)) {
            validateListQuery(operand, context.definition.getProjectId());
            return;
        }
        if ("OPERATION".equals(kind)) {
            JSONArray terms = operand.getJSONArray("terms");
            if (terms != null) {
                for (int i = 0; i < terms.size(); i++) {
                    JSONObject term = terms.getJSONObject(i);
                    validateManagedNodes(term == null ? null : term.getJSONObject("operand"), context);
                }
            }
            return;
        }
        if ("ARRAY".equals(kind)) {
            validateChildren(operand.getJSONArray("items"), context);
        } else if ("ACCESS".equals(kind)) {
            validateManagedNodes(operand.getJSONObject("target"), context);
            validateManagedNodes(operand.getJSONObject("accessor"), context);
        } else if ("CAST".equals(kind)) {
            validateManagedNodes(operand.getJSONObject("operand"), context);
        }
    }

    private void validateChildren(JSONArray children, ExpressionContext context) {
        if (children == null) return;
        for (int i = 0; i < children.size(); i++) {
            validateManagedNodes(children.getJSONObject(i), context);
        }
    }

    private void validateListQuery(JSONObject operand, Long projectId) {
        JSONArray listIds = operand.getJSONArray("listIds");
        if (listIds == null || listIds.isEmpty()) {
            throw new IllegalArgumentException("名单查询至少选择一个名单");
        }
        if (firstText(operand.getString("combinationMode")) == null) {
            throw new IllegalArgumentException("名单组合模式不能为空");
        }
        if (firstText(operand.getString("matchMode")) == null) {
            throw new IllegalArgumentException("名单匹配模式不能为空");
        }
        for (Long listId : longValues(listIds)) {
            RuleListLibrary library = ruleListService.getById(listId);
            boolean global = library != null && RuleListService.SCOPE_GLOBAL.equalsIgnoreCase(library.getScope());
            boolean projectOwned = library != null && projectId != null && projectId.equals(library.getProjectId());
            if (library == null || !Integer.valueOf(1).equals(library.getStatus()) || (!global && !projectOwned)) {
                throw new IllegalArgumentException("名单引用不存在或已停用，ID=" + listId);
            }
        }
    }

    private List<Long> longValues(JSONArray values) {
        List<Long> result = new ArrayList<>();
        if (values == null) return result;
        for (int i = 0; i < values.size(); i++) result.add(values.getLong(i));
        return result;
    }

    private List<String> stringValues(JSONArray values) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (int i = 0; i < values.size(); i++) result.add(values.getString(i));
        return result;
    }

    private String fieldKey(ResolvedField field) {
        return firstText(field.getRefType(), "") + ":"
                + (field.getRefId() == null ? firstText(field.getScriptName(), field.getCode()) : field.getRefId());
    }

    private String sourceType(String refType) {
        if ("DATA_OBJECT".equals(refType)) return "DATA_OBJECT";
        if ("CONSTANT".equals(refType)) return "CONSTANT";
        if ("MODEL".equals(refType) || "MODEL_OUTPUT".equals(refType)) return "MODEL";
        return "INPUT";
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    private static class ExpressionContext {
        private RuleDefinition definition;
        private String mode;
        private JSONObject operand;
        private List<JSONObject> references;
        private Map<String, String> referencePaths;
        private Map<String, Object> constantValues;
        private Map<Long, String> functionCodes;
        private Map<Long, Integer> functionArities;
    }
}
