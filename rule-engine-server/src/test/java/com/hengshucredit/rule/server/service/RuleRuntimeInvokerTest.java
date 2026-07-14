package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleProject;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RuleRuntimeInvokerTest {

    @Test
    public void runtimeBridgeWritesComputedValuesIntoCurrentExecutionFrame() {
        RuleRuntimeInvoker invoker = new RuleRuntimeInvoker();
        Map<String, Object> context = new LinkedHashMap<>();
        invoker.enter("JCLTest", 1L, "TIANSHU", context);
        try {
            RuntimeContextBridge.setValue("age", 22);
            RuntimeContextBridge.setValue("result.decision", "PASS");

            assertEquals(Integer.valueOf(22), context.get("age"));
            assertEquals("PASS", ((Map<?, ?>) context.get("result")).get("decision"));
            assertEquals("JCLTest", RuntimeContextBridge.currentRule().get("code"));
            assertEquals(Long.valueOf(1L), RuntimeContextBridge.currentRule().get("projectId"));
            assertEquals("TIANSHU", RuntimeContextBridge.currentRule().get("projectCode"));
        } finally {
            invoker.exit();
        }

        RuntimeContextBridge.setValue("afterExit", true);
        assertFalse(context.containsKey("afterExit"));
        assertEquals(Collections.emptyMap(), RuntimeContextBridge.currentRule());
    }

    @Test
    public void testModeExecutesCompiledChildByStableIdWithoutPublishing() {
        RuleRuntimeInvoker invoker = new RuleRuntimeInvoker();
        ReflectionTestUtils.setField(invoker, "definitionService", new TestDefinitionService());
        ReflectionTestUtils.setField(invoker, "projectService", new GlobalProjectService());
        ReflectionTestUtils.setField(invoker, "variableSourceResolver", new PassThroughVariableResolver());
        ReflectionTestUtils.setField(invoker, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(invoker, "executionParameterBinder", new ExecutionParameterBinder());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("age", "22");
        invoker.enter("JCLTest", 0L, null, context, true);
        try {
            assertEquals("PASS", invoker.executeRuleById("1"));
            assertEquals("JCLTest", RuntimeContextBridge.currentRule().get("code"));
        } finally {
            invoker.exit();
        }
    }

    @Test
    public void nestedRuleUsesItsRealNameAndRestoresParentRuntimeContext() {
        RuleRuntimeInvoker invoker = new RuleRuntimeInvoker();
        ReflectionTestUtils.setField(invoker, "definitionService", new ContextDefinitionService());
        ReflectionTestUtils.setField(invoker, "projectService", new GlobalProjectService());
        ReflectionTestUtils.setField(invoker, "variableSourceResolver", new PassThroughVariableResolver());
        ReflectionTestUtils.setField(invoker, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(invoker, "executionParameterBinder", new ExecutionParameterBinder());

        invoker.enter("PARENT", 0L, null, new LinkedHashMap<String, Object>(), true);
        try {
            assertEquals("子规则", invoker.executeRuleById("2"));
            assertEquals("PARENT", RuntimeContextBridge.currentRule().get("code"));
        } finally {
            invoker.exit();
        }
    }

    private static class TestDefinitionService extends RuleDefinitionService {
        @Override
        public RuleDefinition getById(Serializable id) {
            RuleDefinition definition = new RuleDefinition();
            definition.setId(1L);
            definition.setProjectId(0L);
            definition.setRuleCode("JCZR");
            definition.setStatus(1);
            return definition;
        }

        @Override
        public RuleDefinitionContent getContent(Long definitionId) {
            RuleDefinitionContent content = new RuleDefinitionContent();
            content.setDefinitionId(definitionId);
            content.setCompileStatus(1);
            content.setCompiledScript("age < 18 ? \"MINOR\" : \"PASS\"");
            return content;
        }

        @Override
        public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
            RuleDefinitionInputField age = new RuleDefinitionInputField();
            age.setScriptName("age");
            age.setFieldType("INTEGER");
            return Collections.singletonList(age);
        }
    }

    private static class GlobalProjectService extends RuleProjectService {
        @Override
        public RuleProject getById(Serializable id) {
            return null;
        }
    }

    private static class ContextDefinitionService extends RuleDefinitionService {
        @Override
        public RuleDefinition getById(Serializable id) {
            RuleDefinition definition = new RuleDefinition();
            definition.setId(2L);
            definition.setProjectId(0L);
            definition.setRuleCode("CHILD");
            definition.setRuleName("子规则");
            definition.setStatus(1);
            return definition;
        }

        @Override
        public RuleDefinitionContent getContent(Long definitionId) {
            RuleDefinitionContent content = new RuleDefinitionContent();
            content.setDefinitionId(definitionId);
            content.setCompileStatus(1);
            content.setCompiledScript("currentRuleName()");
            return content;
        }

        @Override
        public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
            return Collections.emptyList();
        }
    }

    private static class PassThroughVariableResolver extends VariableSourceResolver {
        @Override
        public Map<String, Object> resolve(Long projectId, Map<String, Object> inputParams,
                                           VariableResolveOptions options) {
            return inputParams;
        }
    }
}
