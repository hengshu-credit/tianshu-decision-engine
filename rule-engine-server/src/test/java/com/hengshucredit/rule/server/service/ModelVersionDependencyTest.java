package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleModelVersion;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.artifact.RuleDependencyClosureService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ModelVersionDependencyTest {
    @Test
    public void sharedIdenticalInputIsNotRejectedBecausePackTransportMetadataDiffers() {
        try (var fixture = new ModelVersionExecutionTest.Fixture()) {
            RuleVariable age = new RuleVariable(); age.setId(9L); age.setProjectId(3L); age.setScope("PROJECT");
            age.setVarCode("Age"); age.setScriptName("Age"); age.setVarLabel("年龄"); age.setVarType("DOUBLE"); age.setVarSource("INPUT"); age.setStatus(1);
            RuleModel child = ModelVersionExecutionTest.model(1, "unused", 1);
            String reference = "{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":9,\"code\":\"Age\"}";
            child.getInputFields().get(0).setSourceOperand(reference);
            var saved = new java.util.concurrent.atomic.AtomicReference<RuleModelVersion>();
            ReflectionTestUtils.setField(fixture.tests, "dependencyClosure", new RuleDependencyClosureService() {
                @Override protected RuleVariable loadVariable(Long id) { return age; }
                @Override protected RuleModel loadModel(Long id) { return child; }
                @Override protected RuleModelVersion loadModelVersion(Long id, Integer number) { return saved.get(); }
                @Override protected RuleModelOutputField loadModelOutputField(Long id) { return child.getOutputFields().get(0); }
            });
            saved.set(fixture.capture(child));
            RuleModel parent = ModelVersionExecutionTest.model(1, "unused", 10);
            parent.setId(8L); parent.setModelCode("ParentModel");
            parent.getInputFields().get(0).setId(81L); parent.getInputFields().get(0).setModelId(8L);
            parent.getOutputFields().get(0).setId(82L); parent.getOutputFields().get(0).setModelId(8L);
            parent.getInputFields().get(0).setSourceOperand("{\"kind\":\"OPERATION\",\"terms\":[{\"operand\":" + reference
                    + "},{\"operator\":\"+\",\"operand\":{\"kind\":\"REFERENCE\",\"refType\":\"MODEL_OUTPUT\",\"refId\":61,\"code\":\"HistoricalModel.prediction\"}}]}");
            var result = fixture.tests.execute(fixture.capture(parent), Map.of("Age", 2));
            assertEquals(24d, ((Number) ((Map<?, ?>) result.get("outputs")).get("prediction")).doubleValue(), 0d);
        }
    }

    @Test
    public void parentModelEmbedsSelectedChildVersionIncludingItsOwnFrozenDependencies() {
        try (var fixture = new ModelVersionExecutionTest.Fixture()) {
            RuleModel child = ModelVersionExecutionTest.model(1, "OldAge", 1);
            RuleModelVersion childVersion = fixture.capture(child);
            AtomicInteger liveFieldReads = new AtomicInteger();
            RuleDependencyClosureService closure = new RuleDependencyClosureService() {
                @Override protected RuleModel loadModel(Long id) { return child; }
                @Override protected RuleModelVersion loadModelVersion(Long id, Integer version) { return childVersion; }
                @Override protected RuleModelOutputField loadModelOutputField(Long id) { return child.getOutputFields().get(0); }
                @Override protected List<RuleModelInputField> loadModelInputFields(Long id) { liveFieldReads.incrementAndGet(); return child.getInputFields(); }
                @Override protected List<RuleModelOutputField> loadModelOutputFields(Long id) { liveFieldReads.incrementAndGet(); return child.getOutputFields(); }
            };
            ReflectionTestUtils.setField(fixture.tests, "dependencyClosure", closure);
            RuleModel parent = ModelVersionExecutionTest.model(1, "ignored", 10);
            parent.setId(8L); parent.setModelCode("ParentModel");
            parent.getInputFields().get(0).setId(81L); parent.getInputFields().get(0).setModelId(8L);
            parent.getInputFields().get(0).setSourceOperand("{\"kind\":\"REFERENCE\",\"refType\":\"MODEL_OUTPUT\",\"refId\":61,\"code\":\"HistoricalModel.prediction\"}");
            parent.getOutputFields().get(0).setId(82L); parent.getOutputFields().get(0).setModelId(8L);
            RuleModelVersion parentVersion = fixture.capture(parent);
            assertEquals("子模型已有完整快照时不能读取当前输入输出表", 0, liveFieldReads.get());
            child.getInputFields().get(0).setSourceOperand("{\"kind\":\"PATH\",\"value\":\"NewAge\"}");
            child.setModelContent(ModelVersionExecutionTest.model(2, "NewAge", 100).getModelContent());
            Map<String, Object> result = fixture.tests.execute(parentVersion, Map.of("OldAge", 2, "NewAge", 99));
            assertEquals(true, result.get("success"));
            assertEquals(20d, ((Number) ((Map<?, ?>) result.get("outputs")).get("prediction")).doubleValue(), 0d);
        }
    }
}
