package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ResolvedField;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.model.dto.RuleTestSchemaRequest;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.artifact.RuleDependencyClosureService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ModelVersionSchemaTest {
    @Test
    public void schemaUsesSameFrozenVariableMetadataAsExecutionInsteadOfCurrentCatalog() {
        try (var fixture = new ModelVersionExecutionTest.Fixture()) {
            RuleVariable variable = new RuleVariable();
            variable.setId(9L); variable.setProjectId(3L); variable.setScope("PROJECT");
            variable.setVarCode("OriginalAge"); variable.setScriptName("OriginalAge");
            variable.setVarLabel("原始年龄"); variable.setVarType("DOUBLE");
            variable.setVarSource("INPUT"); variable.setStatus(1);
            ReflectionTestUtils.setField(fixture.tests, "dependencyClosure", new RuleDependencyClosureService() {
                @Override protected RuleVariable loadVariable(Long id) { return variable; }
            });
            AtomicInteger currentSchemaReads = new AtomicInteger();
            RuleTestSchemaService schemas = new RuleTestSchemaService() {
                @Override public RuleTestSchema build(RuleTestSchemaRequest request) {
                    currentSchemaReads.incrementAndGet();
                    ResolvedField field = new ResolvedField();
                    field.setRefId(9L); field.setRefType("VARIABLE"); field.setScriptName("RenamedAge");
                    field.setLabel("新名称"); field.setValueType("STRING");
                    RuleTestSchema schema = new RuleTestSchema();
                    schema.setInputs(List.of(field)); schema.setSampleParams(Map.of("RenamedAge", "new"));
                    return schema;
                }
            };
            FieldDependencyResolver resolver = new FieldDependencyResolver();
            ReflectionTestUtils.setField(resolver, "ruleFieldAnalyzer", new RuleFieldAnalyzer());
            ReflectionTestUtils.setField(schemas, "fieldDependencyResolver", resolver);
            ReflectionTestUtils.setField(fixture.tests, "schemas", schemas);
            var model = ModelVersionExecutionTest.model(1, "unused", 1);
            model.getInputFields().get(0).setSourceOperand("{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":9,\"code\":\"OriginalAge\"}");
            var snapshot = fixture.capture(model);
            RuleTestSchema schema = (RuleTestSchema) fixture.tests.context(snapshot).get("schema");
            assertEquals("冻结模型Schema不允许再用当前MODEL id重新取目录", 0, currentSchemaReads.get());
            assertEquals(1, schema.getInputs().size());
            assertEquals("OriginalAge", schema.getInputs().get(0).getScriptName());
            assertEquals("原始年龄", schema.getInputs().get(0).getLabel());
            assertEquals("DOUBLE", schema.getInputs().get(0).getValueType());
        }
    }
}
