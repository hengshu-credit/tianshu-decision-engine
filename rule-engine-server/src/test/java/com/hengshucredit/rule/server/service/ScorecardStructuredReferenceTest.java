package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.compiler.ScorecardCompiler;
import com.hengshucredit.rule.core.compiler.VarContext;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** 真实UI评分卡同时携带结构化操作数和旧展示字段时，预检必须采用与编译器相同的引用。 */
public class ScorecardStructuredReferenceTest {
    @Test
    public void structuredBetweenDoesNotCreateAnInputFromLegacyDisplayText() {
        String model = model().toJSONString();
        var compiled = new ScorecardCompiler().compile(model, new VarContext(
                Map.of(317L, "uiAge", 318L, "uiScore"), Map.of(),
                Map.of("VARIABLE:317", "uiAge", "VARIABLE:318", "uiScore")));
        assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
        RuleFieldAnalyzer analyzer = new RuleFieldAnalyzer();
        assertEquals("结构化区间已经编译成功，预检输入不能再混入between或旧文本变量",
                List.of("uiAge"), analyzer.extractInputFields(model, "SCORE").stream()
                        .map(RuleDefinitionInputField::getScriptName).toList());
        List<RuleDefinitionInputField> direct = analyzer.extractDirectModelInputFields(model, "SCORE");
        assertEquals(1, direct.size());
        assertEquals(Long.valueOf(317), direct.get(0).getVarId());
        assertEquals("VARIABLE", direct.get(0).getRefType());
    }

    @Test
    public void structuredScorecardAuditsOperandIdentityInsteadOfRedundantLegacyNames() {
        RuleReferenceIntegrityService.AuditReport report = audit(model());
        assertTrue(report.getIssues().stream().map(issue -> issue.getPath() + ':' + issue.getReason()).toList().toString(),
                report.isValid());
    }

    @Test
    public void legacyScorecardUsesItsExistingVarIdContractAndStillRejectsMissingIdentity() {
        JSONObject model = model();
        JSONObject item = model.getJSONArray("scoreItems").getJSONObject(0);
        item.remove("leftOperand");
        item.remove("rightOperand");
        assertTrue("旧评分卡condVar配对的是_varId/_refType", audit(model).isValid());
        item.remove("_varId");
        var report = audit(model);
        assertFalse(report.isValid());
        assertEquals("$.scoreItems[0]", report.getIssues().get(0).getPath());
        assertEquals("MISSING_CONTRACT", report.getIssues().get(0).getReason());
    }

    @Test
    public void legacyConditionOnlyRulesKeepTheirExpressionDependencies() {
        RuleFieldAnalyzer analyzer = new RuleFieldAnalyzer();
        String model = "{\"scoreItems\":[{\"condition\":\"age >= 18 && debt < 100\",\"score\":5}]}";
        assertEquals(List.of("age", "debt"), analyzer.extractInputFields(model, "SCORE").stream()
                .map(RuleDefinitionInputField::getScriptName).toList());
    }

    @Test
    public void validLegacyIdentityCannotHideAMissingOperandIdentity() {
        JSONObject model = model();
        model.getJSONArray("scoreItems").getJSONObject(0).getJSONObject("leftOperand").remove("refId");
        var report = audit(model);
        assertFalse(report.isValid());
        assertTrue(report.getIssues().stream().anyMatch(issue ->
                "$.scoreItems[0].leftOperand".equals(issue.getPath()) && "MISSING_CONTRACT".equals(issue.getReason())));
    }

    private static RuleReferenceIntegrityService.AuditReport audit(JSONObject model) {
        RuleReferenceIntegrityService service = new RuleReferenceIntegrityService();
        ReflectionTestUtils.setField(service, "variableService", new RuleVariableService() {
            @Override public Map<String, String> buildRefScriptNameMap(Long projectId) {
                return Map.of("VARIABLE:317", "uiAge", "VARIABLE:318", "uiScore");
            }
        });
        return service.audit(51L, 17L, model.toJSONString());
    }

    private static JSONObject model() {
        // 与Scorecard.serializeDesignerDraft的UI载荷形态一致；不通过后端创建任何配置。
        return JSON.parseObject("{\"initialScore\":0,\"thresholds\":[],"
                + "\"resultVar\":{\"varCode\":\"uiScore\",\"_varId\":318,\"_refType\":\"VARIABLE\","
                + "\"operand\":{\"kind\":\"REFERENCE\",\"code\":\"uiScore\",\"refId\":318,\"refType\":\"VARIABLE\",\"valueType\":\"NUMBER\"}},"
                + "\"scoreItems\":[{\"leftOperand\":{\"kind\":\"REFERENCE\",\"code\":\"uiAge\",\"refId\":317,\"refType\":\"VARIABLE\",\"valueType\":\"NUMBER\"},"
                + "\"rightOperand\":{\"kind\":\"LITERAL\",\"value\":\"18,60\",\"valueType\":\"NUMBER\"},"
                + "\"condVar\":\"uiAge\",\"condVarType\":\"NUMBER\",\"condOperator\":\"between\",\"condValue\":\"18,60\","
                + "\"_varId\":317,\"_refType\":\"VARIABLE\",\"condition\":\"uiAge between 18,60\",\"score\":5,\"weight\":1}]}");
    }
}
