package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ScorecardLevelFieldsTest {
    @Test
    public void optionalLevelIsAnOutputWithIdentityAndItsValueIsAnInputOnlyWhenEnabled() {
        RuleFieldAnalyzer analyzer = new RuleFieldAnalyzer();
        for (String type : List.of("SCORE", "SCORE_ADV")) {
            JSONObject model = JSON.parseObject("{\"resultVar\":{\"varCode\":\"score\"},"
                    + "\"levelVar\":{\"operand\":{\"kind\":\"REFERENCE\",\"refId\":22,\"refType\":\"VARIABLE\",\"code\":\"Grade_Code\",\"label\":\"风险等级\",\"valueType\":\"STRING\"}},"
                    + "\"thresholds\":[{\"min\":0,\"max\":200,\"resultOperand\":{\"kind\":\"REFERENCE\",\"refId\":23,\"refType\":\"VARIABLE\",\"code\":\"gradeValue\"}}]}");
            var outputs = analyzer.extractOutputFields(model.toJSONString(), type);
            assertEquals(2, outputs.size());
            var level = outputs.get(1);
            assertEquals("Grade_Code", level.getScriptName());
            assertEquals(Long.valueOf(22), level.getVarId());
            assertEquals("VARIABLE", level.getRefType());
            assertEquals("STRING", level.getFieldType());
            assertEquals("风险等级", level.getFieldLabel());
            assertEquals("gradeValue", analyzer.extractDirectModelInputFields(model.toJSONString(), type).get(0).getScriptName());
            var parsed = new RuleModelVarParser().parse(model.toJSONString(), type);
            assertTrue(parsed.getOutputCodes().contains("Grade_Code"));
            assertTrue(parsed.getInputCodes().contains("gradeValue"));
            model.put("levelVar", new JSONObject());
            assertEquals(1, analyzer.extractOutputFields(model.toJSONString(), type).size());
            assertTrue(analyzer.extractDirectModelInputFields(model.toJSONString(), type).isEmpty());
            parsed = new RuleModelVarParser().parse(model.toJSONString(), type);
            assertEquals(java.util.Set.of("score"), parsed.getOutputCodes());
            assertTrue(parsed.getInputCodes().isEmpty());
        }
    }
}
