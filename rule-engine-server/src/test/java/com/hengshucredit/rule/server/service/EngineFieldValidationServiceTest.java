package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EngineFieldValidationServiceTest {

    @Test
    public void apiPlanUsesConfiguredRulesAgainstEffectiveRequestValues() {
        RuleFieldValidationService rules = new RuleFieldValidationService() {
            @Override
            public String validateRuleIds(Long projectId, String json) {
                Assert.assertEquals(Long.valueOf(5L), projectId);
                Assert.assertEquals("[7]", json);
                return json;
            }

            @Override
            public void validateInputFields(List<RuleDefinitionInputField> fields,
                                            Map<String, Object> params) {
                Assert.assertEquals(1, fields.size());
                Assert.assertEquals("email", fields.get(0).getScriptName());
                Assert.assertEquals("[7]", fields.get(0).getValidationRuleIds());
                Assert.assertEquals("a@example.com", params.get("email"));
            }
        };
        EngineFieldValidationService service = new EngineFieldValidationService();
        ReflectionTestUtils.setField(service, "validationService", rules);

        RuleExternalApiConfig api = new RuleExternalApiConfig();
        api.setId(11L);
        api.setExecutionConfig(JSON.toJSONString(Map.of(
                "version", 2,
                "requestFields", List.of(Map.of(
                        "id", "email", "path", "email",
                        "validationRuleIds", List.of(7L))))));
        ExternalApiRequestPlan plan = new ExternalApiRequestPlan(
                api, Map.of(), Map.of("__apiFields", Map.of("email", "a@example.com")), "key");

        service.validateApiPlan(5L, plan);
    }

    @Test
    public void experimentConfigurationUsesStableReferenceKey() {
        RuleFieldValidationService rules = new RuleFieldValidationService() {
            @Override
            public String validateRuleIds(Long projectId, String json) {
                Assert.assertEquals(Long.valueOf(5L), projectId);
                Assert.assertEquals("[9]", json);
                return json;
            }

            @Override
            public void validateInputFields(List<RuleDefinitionInputField> fields,
                                            Map<String, Object> params) {
                Assert.assertEquals("[9]", fields.get(0).getValidationRuleIds());
                Assert.assertEquals(18, params.get("age"));
            }
        };
        EngineFieldValidationService service = new EngineFieldValidationService();
        ReflectionTestUtils.setField(service, "validationService", rules);

        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setRefType("VARIABLE");
        field.setVarId(12L);
        field.setScriptName("age");
        field.setFieldName("age");

        service.validateExperimentInputs(5L,
                JSON.toJSONString(Map.of("VARIABLE:12", List.of(9L))),
                List.of(field), Map.of("age", 18));
    }

    @Test
    public void pathOnlyExperimentBindingUsesPathKey() {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setScriptName("requestId");
        field.setFieldName("requestId");
        Assert.assertEquals("PATH:requestId",
                EngineFieldValidationService.fieldKey(field));
    }
}
