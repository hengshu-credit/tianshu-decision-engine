package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.compiler.*;
import com.hengshucredit.rule.core.engine.QLExpressEngineFactory;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class RuleCompileService {

    private final Map<String, RuleCompiler> compilers = new HashMap<>();

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleDefinitionContentMapper contentMapper;

    @Resource
    private RuleVariableService variableService;

    @Resource
    private RuleFunctionService functionService;

    @Resource
    private RuleCallCycleService ruleCallCycleService;

    public RuleCompileService() {
        compilers.put("TABLE", new DecisionTableCompiler());
        compilers.put("TREE", new DecisionTreeCompiler());
        compilers.put("FLOW", new DecisionFlowCompiler());
        compilers.put("CROSS", new CrossTableCompiler());
        compilers.put("SCORE", new ScorecardCompiler());
        compilers.put("CROSS_ADV", new AdvancedCrossTableCompiler());
        compilers.put("SCORE_ADV", new AdvancedScorecardCompiler());
        compilers.put("SCRIPT", new ScriptPassthroughCompiler());
        compilers.put("RULE_SET", new RuleSetCompiler());
    }

    public CompileResult compile(Long definitionId) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            return CompileResult.fail("规则定义不存在");
        }

        RuleDefinitionContent content = definitionService.getContent(definitionId);
        if (content == null) {
            return CompileResult.fail("规则内容不存在");
        }

        RuleCompiler compiler = compilers.get(definition.getModelType());
        if (compiler == null) {
            return CompileResult.fail("暂不支持的模型类型: " + definition.getModelType());
        }

        String cycleError = ruleCallCycleService.validateNoCycle(definitionId, content.getModelJson());
        if (cycleError != null) {
            return CompileResult.fail(cycleError);
        }

        // 构建变量上下文：通过 varId 查询正确的 scriptName，
        // 以及通过 varCode 回溯 scriptName（设计器未保存 _varId 时兜底）
        VarContext varContext = buildVarContext(definition.getProjectId());

        CompileResult result = compiler.compile(content.getModelJson(), varContext);

        content.setCompileStatus(result.isSuccess() ? 1 : 2);
        content.setCompiledScript(result.getCompiledScript());
        content.setCompiledType(result.getCompiledType());
        content.setCompileMessage(result.isSuccess() ? null : result.getErrorMessage());
        content.setCompileTime(LocalDateTime.now());
        if (result.isSuccess()) {
            content.setScriptMode("visual");
        }
        contentMapper.updateById(content);

        return result;
    }

    /** 编译设计器当前草稿，不覆盖已保存内容或编译状态。 */
    public CompileResult compilePreview(Long definitionId, String modelJson, String modelType) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            return CompileResult.fail("规则定义不存在");
        }
        if (modelJson == null || modelJson.trim().isEmpty()) {
            return CompileResult.fail("规则模型内容不能为空");
        }
        String effectiveType = modelType == null || modelType.trim().isEmpty()
                ? definition.getModelType() : modelType.trim().toUpperCase();
        RuleCompiler compiler = compilers.get(effectiveType);
        if (compiler == null) {
            return CompileResult.fail("暂不支持的模型类型: " + effectiveType);
        }
        String cycleError = ruleCallCycleService.validateNoCycle(definitionId, modelJson);
        if (cycleError != null) {
            return CompileResult.fail(cycleError);
        }
        return compiler.compile(modelJson, buildVarContext(definition.getProjectId()));
    }

    /** 编译单个结构化表达式，不读取或修改规则内容、编译状态和版本。 */
    public CompileResult compileExpression(Long definitionId, Map<String, Object> operand) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            return CompileResult.fail("规则定义不存在");
        }
        if (operand == null || operand.isEmpty()) {
            return CompileResult.fail("表达式不能为空");
        }
        try {
            Map<Long, String> varIdToScriptName = variableService.buildVarIdScriptNameMap(definition.getProjectId());
            Map<String, String> varCodeToScriptName = variableService.buildVarCodeScriptNameMap(definition.getProjectId());
            Map<String, String> refIdToScriptName = variableService.buildRefScriptNameMap(definition.getProjectId());
            Map<Long, String> constantIdToExpression = variableService.buildRefConstantExpressionMap(definition.getProjectId());
            VarContext context = new VarContext(varIdToScriptName, varCodeToScriptName,
                    refIdToScriptName, constantIdToExpression,
                    functionService.buildFunctionCodeMap(definition.getProjectId()),
                    functionService.buildFunctionArityMap(definition.getProjectId()));
            JSONObject json = (JSONObject) JSON.toJSON(operand);
            validateExpressionReferences(json, refIdToScriptName, constantIdToExpression);
            return CompileResult.ok(OperandCompiler.compile(json, context), "QLEXPRESS");
        } catch (RuntimeException e) {
            return CompileResult.fail(e.getMessage() == null ? "表达式编译失败" : e.getMessage());
        }
    }

    private void validateExpressionReferences(JSONObject operand,
                                              Map<String, String> refIdToScriptName,
                                              Map<Long, String> constantIdToExpression) {
        if (operand == null) return;
        String kind = operand.getString("kind");
        if ("REFERENCE".equals(kind)) {
            Long refId = operand.getLong("refId");
            String refType = operand.getString("refType");
            if (refId == null || refType == null || refType.trim().isEmpty()) {
                throw new IllegalArgumentException("受管字段引用缺少 ID 或引用类型");
            }
            if ("CONSTANT".equalsIgnoreCase(refType)) {
                if (!constantIdToExpression.containsKey(refId)) {
                    throw new IllegalArgumentException("常量引用不存在、已停用或值不合法，ID=" + refId);
                }
            } else {
                String key = refType.trim().toUpperCase() + ":" + refId;
                if (!refIdToScriptName.containsKey(key)) {
                    throw new IllegalArgumentException("字段引用不存在或已停用，" + key);
                }
            }
        }
        if ("FUNCTION".equals(kind)) {
            validateExpressionArray(operand.getJSONArray("args"), refIdToScriptName, constantIdToExpression);
        } else if ("OPERATION".equals(kind)) {
            JSONArray terms = operand.getJSONArray("terms");
            if (terms != null) {
                for (int i = 0; i < terms.size(); i++) {
                    JSONObject term = terms.getJSONObject(i);
                    validateExpressionReferences(term == null ? null : term.getJSONObject("operand"),
                            refIdToScriptName, constantIdToExpression);
                }
            }
        } else if ("ACCESS".equals(kind)) {
            validateExpressionReferences(operand.getJSONObject("target"), refIdToScriptName, constantIdToExpression);
            validateExpressionReferences(operand.getJSONObject("accessor"), refIdToScriptName, constantIdToExpression);
        } else if ("CAST".equals(kind)) {
            validateExpressionReferences(operand.getJSONObject("operand"), refIdToScriptName, constantIdToExpression);
        } else if ("ARRAY".equals(kind)) {
            validateExpressionArray(operand.getJSONArray("items"), refIdToScriptName, constantIdToExpression);
        }
    }

    private void validateExpressionArray(JSONArray values,
                                         Map<String, String> refIdToScriptName,
                                         Map<Long, String> constantIdToExpression) {
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) {
            validateExpressionReferences(values.getJSONObject(i), refIdToScriptName, constantIdToExpression);
        }
    }

    private VarContext buildVarContext(Long projectId) {
        Map<Long, String> varIdToScriptName = variableService.buildVarIdScriptNameMap(projectId);
        Map<String, String> varCodeToScriptName = variableService.buildVarCodeScriptNameMap(projectId);
        Map<String, String> refIdToScriptName = variableService.buildRefScriptNameMap(projectId);
        Map<Long, String> constantIdToExpression = variableService.buildRefConstantExpressionMap(projectId);
        return new VarContext(varIdToScriptName, varCodeToScriptName,
                refIdToScriptName, constantIdToExpression,
                functionService.buildFunctionCodeMap(projectId),
                functionService.buildFunctionArityMap(projectId));
    }

    /**
     * 验证手写脚本语法（不覆盖可视化模型编译结果）。
     * 通过 QLExpress 引擎试解析脚本，语法错误立即返回，
     * 运行时错误（变量未定义等）视为语法通过。
     */
    public CompileResult validateScript(String script) {
        CompileResult compileResult = new ScriptPassthroughCompiler().compile(script);
        if (!compileResult.isSuccess()) {
            return compileResult;
        }
        String compiledScript = compileResult.getCompiledScript();
        try {
            com.alibaba.qlexpress4.Express4Runner runner = QLExpressEngineFactory.getInstance();
            runner.execute(compiledScript, Collections.emptyMap(),
                    com.alibaba.qlexpress4.QLOptions.builder().cache(false).build());
            return CompileResult.ok(compiledScript, "QLEXPRESS");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (isSyntaxError(msg)) {
                return CompileResult.fail(msg);
            }
            return CompileResult.ok(compiledScript, "QLEXPRESS");
        }
    }

    /** 区分语法错误和运行时错误（变量缺失等） */
    private boolean isSyntaxError(String msg) {
        String lower = msg.toLowerCase();
        return lower.contains("parse") || lower.contains("syntax")
                || lower.contains("unexpected") || lower.contains("token")
                || lower.contains("解析") || lower.contains("语法");
    }
}
