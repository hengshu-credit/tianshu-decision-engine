package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RuleExecuteServiceTest {

    @Test
    public void executePublishedResolvesExternalVariablesBeforeRunningScript() {
        RuleExecuteService service = new RuleExecuteService();
        RecordingVariableSourceResolver resolver = new RecordingVariableSourceResolver();
        RecordingLogService logService = new RecordingLogService();
        RecordingBillingService billingService = new RecordingBillingService();

        ReflectionTestUtils.setField(service, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(service, "definitionService", new FakeDefinitionService());
        ReflectionTestUtils.setField(service, "projectService", new FakeProjectService());
        ReflectionTestUtils.setField(service, "logService", logService);
        ReflectionTestUtils.setField(service, "functionService", new FakeFunctionService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "billingService", billingService);
        ReflectionTestUtils.setField(service, "variableSourceResolver", resolver);
        NoOpRuntimeInvoker runtimeInvoker = new NoOpRuntimeInvoker();
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", runtimeInvoker);
        ReflectionTestUtils.setField(service, "executionParameterBinder", new ExecutionParameterBinder());

        RulePublished published = new RulePublished();
        published.setDefinitionId(10L);
        published.setRuleCode("RISK_RULE");
        published.setProjectCode("project_a");
        published.setVersion(3);
        published.setModelType("SCRIPT");
        published.setCompiledScript("decision = age >= 18 && externalScore >= 60 ? \"PASS\" : \"REJECT\";\ndecision");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("applicantId", "A001");
        params.put("age", "22");
        params.put("requestMeta", Collections.<String, Object>singletonMap("channel", "APP"));
        ProjectAuthContext authContext = ProjectAuthContext.temporary(1L, "project_a", 11L,
                "BASIC_MAIN", ProjectAuthType.BASIC, 21L, "TOKEN_A", "GRACE");
        RuleExecuteService.ExecutionOutcome outcome = service.executePublishedWithOptions(
                published, params, 1L, "biz-app", VariableResolveOptions.defaults(), "CLIENT_SERVER", authContext);

        assertTrue(outcome.getResult().getErrorMessage(), outcome.getResult().isSuccess());
        assertEquals("PASS", outcome.getResult().getResult());
        assertEquals(Integer.valueOf(22), outcome.getExecuteParams().get("age"));
        assertEquals(Integer.valueOf(80), outcome.getExecuteParams().get("externalScore"));
        assertEquals(Long.valueOf(1), resolver.projectId);
        assertNotNull(resolver.requiredScriptNames);
        assertTrue(resolver.requiredScriptNames.contains("externalScore"));
        assertNotNull(logService.saved);
        assertEquals(1, logService.saveCount);
        assertEquals("RISK_RULE", logService.saved.getRuleCode());
        assertEquals("biz-app", logService.saved.getClientAppName());
        assertEquals("BASIC_MAIN", logService.saved.getAuthCode());
        assertEquals("TOKEN_A", logService.saved.getTokenCode());
        assertEquals("GRACE", logService.saved.getAuthPhase());
        assertTrue(logService.saved.getInputParams().contains("\"age\":22"));
        assertTrue(logService.saved.getInputParams().contains("\"channel\":\"APP\""));
        assertFalse(logService.saved.getInputParams().contains("DERIVED"));
        assertFalse(logService.saved.getInputParams().contains("externalScore"));
        assertEquals(outcome.getResult().getTraceId(), logService.saved.getTraceId());
        assertTrue(logService.saved.getTraceInfo().contains(outcome.getResult().getTraceId()));
        assertSame(outcome.getExecuteParams(), resolver.target);
        assertSame(outcome.getExecuteParams(), runtimeInvoker.values);
        assertFalse(runtimeInvoker.originalInput.containsKey("externalScore"));
        assertEquals(authContext, billingService.authContext);

        RuleExecuteService.ExecutionOutcome experimentOutcome = service.executePublishedWithOptions(
                published, params, 1L, "biz-app", VariableResolveOptions.defaults(),
                "EXPERIMENT_TEST", authContext);
        assertTrue(experimentOutcome.getResult().isSuccess());
        assertEquals("普通执行日志不得重复记录实验组内部规则", 1, logService.saveCount);
        assertNotNull("实验组内部规则仍需生成 trace", experimentOutcome.getResult().getTraceId());
    }

    private static class FakeDefinitionService extends RuleDefinitionService {
        @Override
        public RuleDefinition getById(Serializable id) {
            RuleDefinition definition = new RuleDefinition();
            definition.setId(10L);
            definition.setProjectId(1L);
            definition.setRuleCode("RISK_RULE");
            definition.setCurrentVersion(3);
            definition.setModelType("SCRIPT");
            definition.setScope("PROJECT");
            return definition;
        }

        @Override
        public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
            RuleDefinitionInputField externalScore = new RuleDefinitionInputField();
            externalScore.setScriptName("externalScore");
            externalScore.setFieldType("INTEGER");
            RuleDefinitionInputField age = new RuleDefinitionInputField();
            age.setScriptName("age");
            age.setFieldType("INTEGER");
            return java.util.Arrays.asList(externalScore, age);
        }
    }

    private static class FakeProjectService extends RuleProjectService {
        @Override
        public RuleProject getById(Serializable id) {
            RuleProject project = new RuleProject();
            project.setId(1L);
            project.setProjectCode("project_a");
            project.setTraceScopeCode("0001");
            return project;
        }
    }

    private static class FakeFunctionService extends RuleFunctionService {
        @Override
        public List<RuleFunction> listByProject(Long projectId) {
            return Collections.emptyList();
        }
    }

    private static class RecordingVariableSourceResolver extends VariableSourceResolver {
        private Long projectId;
        private Set<String> requiredScriptNames;
        private Map<String, Object> target;

        @Override
        public Map<String, Object> resolveInto(Long projectId, Map<String, Object> target,
                                               VariableResolveOptions options) {
            this.projectId = projectId;
            this.requiredScriptNames = options == null ? null : options.getRequiredScriptNames();
            this.target = target;
            target.put("externalScore", 80);
            ((Map<String, Object>) target.get("requestMeta")).put("channel", "DERIVED");
            return target;
        }
    }

    private static class RecordingLogService extends RuleExecutionLogService {
        private RuleExecutionLog saved;
        private int saveCount;

        @Override
        public boolean save(RuleExecutionLog entity) {
            saveCount++;
            this.saved = entity;
            return true;
        }
    }

    private static class RecordingBillingService extends RuleBillingService {
        private ProjectAuthContext authContext;

        @Override
        public void recordEngineExecution(RuleDefinition definition, boolean success, Long executeTimeMs,
                                          String errorMessage, ProjectAuthContext authContext) {
            this.authContext = authContext;
            RuleResult ignored = new RuleResult();
            ignored.setSuccess(success);
        }
    }

    private static class NoOpRuntimeInvoker extends RuleRuntimeInvoker {
        private Map<String, Object> values;
        private Map<String, Object> originalInput;

        @Override
        public void register(com.alibaba.qlexpress4.Express4Runner runner) {
        }

        @Override
        public void enter(String ruleCode, Long projectId, String projectCode, Map<String, Object> context) {
        }

        @Override
        public void enter(String ruleCode, Long projectId, String projectCode,
                          Map<String, Object> context, boolean testMode) {
        }

        @Override
        public void enter(RuleDefinition definition, String projectCode,
                          Map<String, Object> values, Map<String, Object> originalInput,
                          boolean testMode) {
            this.values = values;
            this.originalInput = new LinkedHashMap<>(originalInput);
        }

        @Override
        public void enter(RuleDefinition definition, Long executionProjectId, String projectCode,
                          Map<String, Object> values, Map<String, Object> originalInput,
                          boolean testMode) {
            this.values = values;
            this.originalInput = new LinkedHashMap<>(originalInput);
        }

        @Override
        public void completeRoot(RuleResult result) {
            result.setTraceId("QLP000120260715123456789ABCDEF012345");
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("traceId", result.getTraceId());
            trace.put("children", Collections.emptyList());
            result.setTraces(Collections.<Object>singletonList(trace));
        }

        @Override
        public void exit() {
        }
    }
}
