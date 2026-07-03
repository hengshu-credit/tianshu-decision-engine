package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RulePublished;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RuleExecuteServiceTest {

    @Test
    public void executePublishedResolvesExternalVariablesBeforeRunningScript() {
        RuleExecuteService service = new RuleExecuteService();
        RecordingVariableSourceResolver resolver = new RecordingVariableSourceResolver();
        RecordingLogService logService = new RecordingLogService();

        ReflectionTestUtils.setField(service, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(service, "definitionService", new FakeDefinitionService());
        ReflectionTestUtils.setField(service, "projectService", new FakeProjectService());
        ReflectionTestUtils.setField(service, "logService", logService);
        ReflectionTestUtils.setField(service, "functionService", new FakeFunctionService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());
        ReflectionTestUtils.setField(service, "variableSourceResolver", resolver);
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", new NoOpRuntimeInvoker());

        RulePublished published = new RulePublished();
        published.setDefinitionId(10L);
        published.setRuleCode("RISK_RULE");
        published.setProjectCode("project_a");
        published.setVersion(3);
        published.setModelType("SCRIPT");
        published.setCompiledScript("decision = externalScore >= 60 ? \"PASS\" : \"REJECT\";\ndecision");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("applicantId", "A001");
        RuleExecuteService.ExecutionOutcome outcome = service.executePublishedWithOptions(
                published, params, 1L, "biz-app", VariableResolveOptions.defaults(), "CLIENT_SERVER");

        assertTrue(outcome.getResult().getErrorMessage(), outcome.getResult().isSuccess());
        assertEquals("PASS", outcome.getResult().getResult());
        assertEquals(Integer.valueOf(80), outcome.getExecuteParams().get("externalScore"));
        assertEquals(Long.valueOf(1), resolver.projectId);
        assertNotNull(resolver.requiredScriptNames);
        assertTrue(resolver.requiredScriptNames.contains("externalScore"));
        assertNotNull(logService.saved);
        assertEquals("RISK_RULE", logService.saved.getRuleCode());
        assertEquals("biz-app", logService.saved.getClientAppName());
        assertTrue(logService.saved.getInputParams().contains("externalScore"));
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
            return definition;
        }

        @Override
        public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
            RuleDefinitionInputField field = new RuleDefinitionInputField();
            field.setScriptName("externalScore");
            return Collections.singletonList(field);
        }
    }

    private static class FakeProjectService extends RuleProjectService {
        @Override
        public RuleProject getById(Serializable id) {
            RuleProject project = new RuleProject();
            project.setId(1L);
            project.setProjectCode("project_a");
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

        @Override
        public Map<String, Object> resolve(Long projectId, Map<String, Object> inputParams, VariableResolveOptions options) {
            this.projectId = projectId;
            this.requiredScriptNames = options == null ? null : options.getRequiredScriptNames();
            Map<String, Object> resolved = new LinkedHashMap<>(inputParams);
            resolved.put("externalScore", 80);
            return resolved;
        }
    }

    private static class RecordingLogService extends RuleExecutionLogService {
        private RuleExecutionLog saved;

        @Override
        public boolean save(RuleExecutionLog entity) {
            this.saved = entity;
            return true;
        }
    }

    private static class RecordingBillingService extends RuleBillingService {
        @Override
        public void recordEngineExecution(RuleDefinition definition, boolean success, Long executeTimeMs, String errorMessage) {
            RuleResult ignored = new RuleResult();
            ignored.setSuccess(success);
        }
    }

    private static class NoOpRuntimeInvoker extends RuleRuntimeInvoker {
        @Override
        public void register(com.alibaba.qlexpress4.Express4Runner runner) {
        }

        @Override
        public void enter(String ruleCode, Long projectId, String projectCode, Map<String, Object> context) {
        }

        @Override
        public void exit() {
        }
    }
}
