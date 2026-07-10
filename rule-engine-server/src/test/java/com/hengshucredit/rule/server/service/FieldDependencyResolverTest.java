package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ResolutionPlan;
import com.hengshucredit.rule.model.dto.RuleTestSchemaRequest;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FieldDependencyResolverTest {

    @Test
    public void savedRuleUsesPersistedResolvedFieldsWithoutOutputsAsInputs() {
        FieldDependencyResolver resolver = new FieldDependencyResolver();
        ReflectionTestUtils.setField(resolver, "definitionService", new RuleDefinitionService() {
            @Override
            public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
                return Arrays.asList(input("score_f1_fields.HYBASE_X115", "DOUBLE", "DATA_OBJECT"),
                        input("age", "INTEGER", "VARIABLE"));
            }

            @Override
            public List<RuleDefinitionOutputField> listOutputFields(Long definitionId) {
                RuleDefinitionOutputField output = new RuleDefinitionOutputField();
                output.setScriptName("score_f1.score");
                output.setFieldType("DOUBLE");
                output.setRefType("MODEL_OUTPUT");
                return Collections.singletonList(output);
            }
        });
        RuleTestSchemaRequest request = new RuleTestSchemaRequest();
        request.setTargetType("RULE");
        request.setTargetId(7L);

        ResolutionPlan plan = resolver.resolve(request);

        assertEquals(2, plan.getExternalInputs().size());
        assertTrue(plan.getExternalInputs().stream().anyMatch(f -> "score_f1_fields.HYBASE_X115".equals(f.getScriptName())));
        assertFalse(plan.getExternalInputs().stream().anyMatch(f -> "score_f1.score".equals(f.getScriptName())));
        assertEquals("score_f1.score", plan.getOutputs().get(0).getScriptName());
    }

    private static RuleDefinitionInputField input(String path, String type, String refType) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setScriptName(path);
        field.setFieldName(path);
        field.setFieldType(type);
        field.setRefType(refType);
        field.setStatus(1);
        return field;
    }
}
