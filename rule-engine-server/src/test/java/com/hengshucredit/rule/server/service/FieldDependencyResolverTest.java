package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ResolutionPlan;
import com.hengshucredit.rule.model.dto.RuleTestSchemaRequest;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExperimentGroup;
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
            public RuleDefinition getById(java.io.Serializable id) {
                return null;
            }

            @Override
            public RuleDefinitionContent getContent(Long definitionId) {
                return null;
            }

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

    @Test
    public void savedRuleSchemaReanalyzesCurrentContentAndDropsStaleLiteralFields() {
        FieldDependencyResolver resolver = new FieldDependencyResolver();
        ReflectionTestUtils.setField(resolver, "definitionService", new RuleDefinitionService() {
            @Override
            public RuleDefinition getById(java.io.Serializable id) {
                RuleDefinition definition = new RuleDefinition();
                definition.setId(6L);
                definition.setProjectId(1L);
                definition.setModelType("FLOW");
                return definition;
            }

            @Override
            public RuleDefinitionContent getContent(Long definitionId) {
                RuleDefinitionContent content = new RuleDefinitionContent();
                content.setModelJson("{\"nodes\":[]}");
                return content;
            }
        });
        ReflectionTestUtils.setField(resolver, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override
            public ResolvedFields resolveFields(Long definitionId, String modelJson, String modelType, Long projectId) {
                return new ResolvedFields(Arrays.asList(
                        input("idcard_no", "STRING", "VARIABLE"),
                        input("credit_time", "STRING", "VARIABLE"),
                        input("score_f1_fields.HYBASE_X115", "DOUBLE", "DATA_OBJECT")
                ), Collections.emptyList());
            }
        });
        RuleTestSchemaRequest request = new RuleTestSchemaRequest();
        request.setTargetType("RULE");
        request.setTargetId(6L);

        ResolutionPlan plan = resolver.resolve(request);

        assertTrue(plan.getExternalInputs().stream().anyMatch(f -> "score_f1_fields.HYBASE_X115".equals(f.getScriptName())));
        assertFalse(plan.getExternalInputs().stream().anyMatch(f -> "DAY".equals(f.getScriptName())));
    }

    @Test
    public void experimentMergesReferencedRuleAndRoutingConditionInputs() {
        FieldDependencyResolver resolver = new FieldDependencyResolver();
        ReflectionTestUtils.setField(resolver, "definitionService", new RuleDefinitionService() {
            @Override
            public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
                return Collections.singletonList(input("score_f1_fields.HYBASE_X115", "DOUBLE", "DATA_OBJECT"));
            }

            @Override
            public List<RuleDefinitionOutputField> listOutputFields(Long definitionId) {
                RuleDefinitionOutputField output = new RuleDefinitionOutputField();
                output.setScriptName("decision");
                output.setFieldType("STRING");
                return Collections.singletonList(output);
            }
        });
        ReflectionTestUtils.setField(resolver, "experimentService", new RuleExperimentService() {
            @Override
            public RuleExperiment getDetail(Long id) {
                RuleExperiment experiment = new RuleExperiment();
                experiment.setId(id);
                experiment.setProjectId(1L);
                experiment.setRequestKeyPath("request.id");
                RuleExperimentGroup group = new RuleExperimentGroup();
                group.setConditionConfig("{\"varCode\":\"customer.level\",\"operator\":\"==\",\"value\":\"A\"}");
                experiment.setGroups(Collections.singletonList(group));
                return experiment;
            }

            @Override
            public List<Long> listReferencedDefinitionIds(Long experimentId) {
                return Collections.singletonList(7L);
            }

            @Override
            public RuleFieldAnalyzer.ResolvedFields resolveTestFields(Long experimentId) {
                RuleDefinitionOutputField output = new RuleDefinitionOutputField();
                output.setScriptName("decision");
                output.setFieldType("STRING");
                return new RuleFieldAnalyzer.ResolvedFields(Arrays.asList(
                        input("score_f1_fields.HYBASE_X115", "DOUBLE", "DATA_OBJECT"),
                        input("customer.level", "STRING", "DATA_OBJECT"),
                        input("request.id", "STRING", "VARIABLE")
                ), Collections.singletonList(output));
            }
        });
        ReflectionTestUtils.setField(resolver, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override
            public ResolvedFields resolveFields(Long definitionId, String modelJson, String modelType, Long projectId) {
                return new ResolvedFields(Collections.singletonList(input("customer.level", "STRING", "DATA_OBJECT")), Collections.emptyList());
            }
        });

        RuleTestSchemaRequest request = new RuleTestSchemaRequest();
        request.setTargetType("EXPERIMENT");
        request.setTargetId(3L);
        ResolutionPlan plan = resolver.resolve(request);

        assertEquals(3, plan.getExternalInputs().size());
        assertTrue(plan.getExternalInputs().stream().anyMatch(f -> "score_f1_fields.HYBASE_X115".equals(f.getScriptName())));
        assertTrue(plan.getExternalInputs().stream().anyMatch(f -> "customer.level".equals(f.getScriptName())));
        assertTrue(plan.getExternalInputs().stream().anyMatch(f -> "request.id".equals(f.getScriptName())));
        assertEquals("decision", plan.getOutputs().get(0).getScriptName());
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
