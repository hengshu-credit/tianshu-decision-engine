package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.RuleTraceFrame;
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void childRuleContinuesOnExactSameSessionAndValuesMap() {
        RuleRuntimeInvoker invoker = new RuleRuntimeInvoker();
        ReflectionTestUtils.setField(invoker, "definitionService", new SharedSessionDefinitionService());
        ReflectionTestUtils.setField(invoker, "projectService", new GlobalProjectService());
        RecordingChildVariableResolver variableResolver = new RecordingChildVariableResolver();
        ReflectionTestUtils.setField(invoker, "variableSourceResolver", variableResolver);
        ReflectionTestUtils.setField(invoker, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(invoker, "executionParameterBinder", new ExecutionParameterBinder());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("CREDIT_AMOUNT", 1000);
        invoker.enter("PARENT", 0L, null, values, true);
        try {
            RuleExecutionSession session = invoker.currentSession();
            assertNotNull(session);
            assertSame(values, session.getValues());

            assertEquals(3000, ((Number) invoker.executeRuleById("3")).intValue());
            assertSame(session, invoker.currentSession());
            assertSame(values, session.getValues());
            assertEquals(3000, ((Number) values.get("CREDIT_AMOUNT")).intValue());

            RuleResult rootResult = new RuleResult();
            rootResult.setSuccess(true);
            rootResult.setExecuteTimeMs(2L);
            rootResult.setTraces(Collections.<Object>singletonList("ROOT_RAW_TRACE"));
            invoker.completeRoot(rootResult);

            assertNotNull(rootResult.getTraceId());
            assertEquals(1, rootResult.getTraces().size());
            RuleTraceFrame root = (RuleTraceFrame) rootResult.getTraces().get(0);
            assertEquals(rootResult.getTraceId(), root.getTraceId());
            assertEquals(1, root.getChildren().size());
            RuleTraceFrame child = root.getChildren().get(0);
            assertTrue(child.getTraceId().startsWith("RSG0000"));
            assertNotEquals(root.getTraceId(), child.getTraceId());
            assertEquals(child.getTraceId(), variableResolver.ruleTraceIdDuringResolve);
            assertFalse(child.getExpressionTrace().isEmpty());
        } finally {
            invoker.exit();
        }
    }

    @Test
    public void eachOutermostExecutionCreatesDifferentSession() {
        RuleRuntimeInvoker invoker = new RuleRuntimeInvoker();
        Map<String, Object> firstValues = new LinkedHashMap<>();
        invoker.enter("FIRST", 0L, null, firstValues, true);
        RuleExecutionSession firstSession = invoker.currentSession();
        invoker.exit();

        Map<String, Object> secondValues = new LinkedHashMap<>();
        invoker.enter("SECOND", 0L, null, secondValues, true);
        try {
            assertNotSame(firstSession, invoker.currentSession());
            assertSame(secondValues, invoker.currentSession().getValues());
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

    private static class SharedSessionDefinitionService extends RuleDefinitionService {
        @Override
        public RuleDefinition getById(Serializable id) {
            RuleDefinition definition = new RuleDefinition();
            definition.setId(3L);
            definition.setProjectId(0L);
            definition.setRuleCode("CREDIT_CHILD");
            definition.setRuleName("授信额度子规则");
            definition.setModelType("RULE_SET");
            definition.setScope("GLOBAL");
            definition.setStatus(1);
            return definition;
        }

        @Override
        public RuleDefinitionContent getContent(Long definitionId) {
            RuleDefinitionContent content = new RuleDefinitionContent();
            content.setDefinitionId(definitionId);
            content.setCompileStatus(1);
            content.setCompiledScript("CREDIT_AMOUNT = 3000; CREDIT_AMOUNT");
            return content;
        }

        @Override
        public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
            RuleDefinitionInputField amount = new RuleDefinitionInputField();
            amount.setScriptName("CREDIT_AMOUNT");
            amount.setFieldType("INTEGER");
            return Collections.singletonList(amount);
        }
    }

    private static class RecordingChildVariableResolver extends VariableSourceResolver {
        private String ruleTraceIdDuringResolve;

        @Override
        public Map<String, Object> resolveInto(Long projectId, Map<String, Object> target,
                                               VariableResolveOptions options) {
            Object traceId = RuntimeContextBridge.currentRule().get("traceId");
            ruleTraceIdDuringResolve = traceId == null ? null : String.valueOf(traceId);
            Map<String, Object> resolvedCopy = new LinkedHashMap<>(target);
            target.putAll(resolvedCopy);
            return target;
        }
    }

    private static class PassThroughVariableResolver extends VariableSourceResolver {
        @Override
        public Map<String, Object> resolveInto(Long projectId, Map<String, Object> target,
                                               VariableResolveOptions options) {
            return target;
        }
    }
}
