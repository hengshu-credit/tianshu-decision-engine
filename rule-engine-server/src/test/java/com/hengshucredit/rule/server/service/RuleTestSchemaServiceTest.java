package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ResolutionPlan;
import com.hengshucredit.rule.model.dto.ResolvedField;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.model.dto.RuleTestSchemaRequest;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleTestSchemaServiceTest {

    @Test
    public void buildsNestedSamplesFromResolvedExternalInputsOnly() {
        ResolutionPlan plan = new ResolutionPlan();
        plan.setExternalInputs(Arrays.asList(
                field("score_f1_fields.HYBASE_X115", "DOUBLE", "DATA_OBJECT"),
                field("score_f1_fields.HYDK_X760", "NUMBER", "DATA_OBJECT"),
                field("age", "INTEGER", "INPUT")
        ));
        plan.setOutputs(Collections.singletonList(field("score_f1.score", "DOUBLE", "MODEL_OUTPUT")));
        FieldDependencyResolver resolver = new FieldDependencyResolver() {
            @Override
            public ResolutionPlan resolve(RuleTestSchemaRequest request) {
                return plan;
            }
        };
        RuleTestSchemaService service = new RuleTestSchemaService();
        ReflectionTestUtils.setField(service, "fieldDependencyResolver", resolver);

        RuleTestSchema schema = service.build(new RuleTestSchemaRequest());

        assertTrue(schema.getSampleParams().get("score_f1_fields") instanceof Map);
        Map<?, ?> scoreFields = (Map<?, ?>) schema.getSampleParams().get("score_f1_fields");
        assertEquals(0d, ((Number) scoreFields.get("HYBASE_X115")).doubleValue(), 0d);
        assertEquals(0d, ((Number) scoreFields.get("HYDK_X760")).doubleValue(), 0d);
        assertEquals(Integer.valueOf(0), schema.getSampleParams().get("age"));
        assertFalse(schema.getSampleParams().containsKey("score_f1.score"));
        assertFalse(schema.getSampleParams().containsKey("DAY"));
    }

    @Test
    public void reportsParentChildPathConflictsWithoutOverwritingExistingValue() {
        ResolutionPlan plan = new ResolutionPlan();
        ResolvedField parent = field("request", "STRING", "INPUT");
        parent.setDefaultValue("raw-request");
        plan.setExternalInputs(Arrays.asList(parent, field("request.id", "STRING", "DATA_OBJECT")));
        RuleTestSchemaService service = new RuleTestSchemaService();
        ReflectionTestUtils.setField(service, "fieldDependencyResolver", new FieldDependencyResolver() {
            @Override
            public ResolutionPlan resolve(RuleTestSchemaRequest request) {
                return plan;
            }
        });

        RuleTestSchema schema = service.build(new RuleTestSchemaRequest());

        assertEquals("raw-request", schema.getSampleParams().get("request"));
        assertTrue(schema.getDiagnostics().stream().anyMatch(message -> message.contains("request.id")));
    }

    private static ResolvedField field(String path, String type, String sourceType) {
        ResolvedField field = new ResolvedField();
        field.setCode(path);
        field.setScriptName(path);
        field.setValueType(type);
        field.setSourceType(sourceType);
        return field;
    }
}
