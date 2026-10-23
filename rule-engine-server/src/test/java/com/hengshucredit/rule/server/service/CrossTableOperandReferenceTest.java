package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class CrossTableOperandReferenceTest {
    @Test public void advancedScorecardConditionUsesOperandInsteadOfItsDisplayCode() {
        JSONObject model = model("SCORE_ADV");
        JSONObject condition = JSON.parseObject("{\"varCode\":\"18\",\"leftOperand\":{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":18},\"operator\":\"==\",\"rightOperand\":{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":18}}");
        JSONObject dimension = model.getJSONArray("dimensionGroups").getJSONObject(0).getJSONArray("dimensions").getJSONObject(0);
        dimension.put("rules", JSON.parseArray("[{\"score\":5,\"conditions\":[" + condition.toJSONString() + "]}]"));
        assertTrue(new RuleFieldAnalyzer().extractInputFields(model.toJSONString(), "SCORE_ADV").isEmpty());
        assertTrue(audit(model).isValid());
        dimension.getJSONArray("rules").getJSONObject(0).getJSONArray("conditions").getJSONObject(0).getJSONObject("leftOperand").put("kind", "REFERENCE");
        assertFalse(audit(model).isValid());
    }
    @Test public void literalDimensionsDoNotBecomeNamedInputsOrManagedReferences() {
        for (String type : List.of("CROSS", "CROSS_ADV", "SCORE_ADV")) {
            JSONObject model = model(type);
            var analyzer = new RuleFieldAnalyzer();
            assertTrue(type + " 的固定值不属于调用方输入", analyzer.extractInputFields(model.toJSONString(), type).isEmpty());
            var report = audit(model);
            assertTrue(report.getIssues().stream().map(issue -> issue.getPath() + ':' + issue.getReason()).toList().toString(), report.isValid());
        }
    }

    @Test public void outputUsesOperandIdentityAndDoesNotExposeOldDisplayCode() {
        for (String type : List.of("CROSS", "CROSS_ADV", "SCORE", "SCORE_ADV")) {
            JSONObject model = model(type);
            model.getJSONObject("resultVar").put("varCode", "Old_Display");
            var outputs = new RuleFieldAnalyzer().extractOutputFields(model.toJSONString(), type);
            assertEquals(type, List.of("Result"), outputs.stream().map(com.hengshucredit.rule.model.entity.RuleDefinitionOutputField::getScriptName).toList());
        }
    }

    @Test public void malformedManagedOperandStillFailsAndLiteralObjectDataIsNotAuditedAsReferences() {
        JSONObject model = model("CROSS");
        model.getJSONObject("rowVar").put("operand", JSON.parseObject("{\"kind\":\"REFERENCE\",\"code\":\"Missing\",\"refType\":\"VARIABLE\"}"));
        assertFalse(audit(model).isValid());
        JSONObject literal = JSON.parseObject("{\"kind\":\"LITERAL\",\"valueType\":\"OBJECT\",\"value\":{\"varCode\":\"businessData\",\"refCode\":\"opaque\"}}");
        assertTrue(audit(literal).isValid());
    }

    private static RuleReferenceIntegrityService.AuditReport audit(JSONObject model) {
        var service = new RuleReferenceIntegrityService();
        ReflectionTestUtils.setField(service, "variableService", new RuleVariableService() {
            @Override public Map<String,String> buildRefScriptNameMap(Long id) { return Map.of("VARIABLE:9", "Result"); }
        });
        return service.audit(7L, 1L, model.toJSONString());
    }
    private static JSONObject model(String type) {
        JSONObject result = JSON.parseObject("{\"resultVar\":{\"varCode\":\"Result\",\"_varId\":9,\"_refType\":\"VARIABLE\",\"operand\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":9,\"code\":\"Result\",\"valueType\":\"NUMBER\"}}}");
        JSONObject row = JSON.parseObject("{\"varCode\":\"1\",\"operand\":{\"kind\":\"LITERAL\",\"value\":1,\"valueType\":\"NUMBER\"},\"segments\":[{\"operator\":\"==\",\"valueOperand\":{\"kind\":\"LITERAL\",\"value\":1,\"valueType\":\"NUMBER\"}}]}");
        if ("CROSS".equals(type)) { result.put("rowVar",row); result.put("colVar",row); }
        else if ("CROSS_ADV".equals(type)) { result.put("rowDimensions",new com.alibaba.fastjson.JSONArray(List.of(row))); result.put("colDimensions",new com.alibaba.fastjson.JSONArray(List.of(row))); }
        else if ("SCORE_ADV".equals(type)) {
            JSONObject group = new JSONObject(); group.put("dimensions", new com.alibaba.fastjson.JSONArray(List.of(row)));
            result.put("dimensionGroups", new com.alibaba.fastjson.JSONArray(List.of(group)));
        }
        return result;
    }
}
