package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    public void allRulesTerminationReturnsRootOutputContractAsSuccess() {
        RuleExecuteService service = new RuleExecuteService();
        QLExpressEngine engine = new QLExpressEngine();
        TerminationDefinitionService definitionService = new TerminationDefinitionService();
        FakeProjectService projectService = new FakeProjectService();
        RecordingLogService logService = new RecordingLogService();
        RecordingBillingService billingService = new RecordingBillingService();
        PassThroughVariableResolver resolver = new PassThroughVariableResolver();
        ExecutionParameterBinder binder = new ExecutionParameterBinder();
        RuleRuntimeInvoker runtimeInvoker = new RuleRuntimeInvoker();

        ReflectionTestUtils.setField(runtimeInvoker, "definitionService", definitionService);
        ReflectionTestUtils.setField(runtimeInvoker, "projectService", projectService);
        ReflectionTestUtils.setField(runtimeInvoker, "variableSourceResolver", resolver);
        ReflectionTestUtils.setField(runtimeInvoker, "qlExpressEngine", engine);
        ReflectionTestUtils.setField(runtimeInvoker, "executionParameterBinder", binder);

        ReflectionTestUtils.setField(service, "qlExpressEngine", engine);
        ReflectionTestUtils.setField(service, "definitionService", definitionService);
        ReflectionTestUtils.setField(service, "projectService", projectService);
        ReflectionTestUtils.setField(service, "logService", logService);
        ReflectionTestUtils.setField(service, "functionService", new FakeFunctionService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "billingService", billingService);
        ReflectionTestUtils.setField(service, "variableSourceResolver", resolver);
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", runtimeInvoker);
        ReflectionTestUtils.setField(service, "executionParameterBinder", binder);

        RulePublished published = new RulePublished();
        published.setDefinitionId(10L);
        published.setRuleCode("RISK_RULE");
        published.setProjectCode("project_a");
        published.setVersion(3);
        published.setModelType("DECISION_FLOW");
        published.setCompiledScript("decision = \"STOP\"; "
                + "setRuntimeValue(\"decision\", decision); "
                + "terminateAllRules(); "
                + "setRuntimeValue(\"afterEnd\", true)");

        RuleResult result = service.executePublished(
                published, Collections.<String, Object>emptyMap(), 1L, "biz-app");

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getResult() instanceof Map);
        assertEquals("STOP", ((Map<?, ?>) result.getResult()).get("decision"));
        assertTrue(((Map<?, ?>) result.getResult()).containsKey("notAssigned"));
        assertEquals(null, ((Map<?, ?>) result.getResult()).get("notAssigned"));
        assertFalse(logService.saved.getOutputResult().contains("afterEnd"));

        published.setCompiledScript("decision = \"PASS\"; setRuntimeValue(\"decision\", decision);");
        RuleResult normalResult = service.executePublished(
                published, Collections.<String, Object>emptyMap(), 1L, "biz-app");

        assertTrue(normalResult.getErrorMessage(), normalResult.isSuccess());
        assertTrue(normalResult.getResult() instanceof Map);
        assertEquals("PASS", ((Map<?, ?>) normalResult.getResult()).get("decision"));
        assertTrue(((Map<?, ?>) normalResult.getResult()).containsKey("notAssigned"));
    }

    @Test
    public void previewExecutionCompilesAndRunsCurrentDesignerModelWithCurrentFields() {
        RuleExecuteService service = new RuleExecuteService();
        RecordingLogService logService = new RecordingLogService();
        RecordingBillingService billingService = new RecordingBillingService();
        RecordingPreviewResolver resolver = new RecordingPreviewResolver();

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
        ReflectionTestUtils.setField(service, "compileService", new RuleCompileService() {
            @Override
            public CompileResult compilePreview(Long definitionId, String modelJson, String modelType) {
                assertEquals("{\"script\":\"draft\"}", modelJson);
                assertEquals("SCRIPT", modelType);
                return CompileResult.ok("decision = draftValue; decision", "QLEXPRESS");
            }
        });
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override
            public ResolvedFields resolveFields(Long definitionId, String modelJson, String modelType, Long projectId) {
                RuleDefinitionInputField field = new RuleDefinitionInputField();
                field.setScriptName("draftValue");
                field.setFieldType("INTEGER");
                return new ResolvedFields(Collections.singletonList(field), Collections.emptyList());
            }
        });

        RuleResult result = service.testExecutePreview(10L, "{\"script\":\"draft\"}", "SCRIPT",
                Collections.<String, Object>singletonMap("draftValue", "7"));

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(Integer.valueOf(7), result.getResult());
        assertTrue(resolver.requiredScriptNames.contains("draftValue"));
        assertTrue(logService.saved.getInputParams().contains("\"draftValue\":7"));
        assertEquals("{\"script\":\"draft\"}", runtimeInvoker.modelJson);
    }

    @Test
    public void previewExecutionWithNoInputsRequiresNoSourceVariables() {
        RuleExecuteService service = new RuleExecuteService();
        RecordingPreviewResolver resolver = new RecordingPreviewResolver();

        ReflectionTestUtils.setField(service, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(service, "definitionService", new FakeDefinitionService());
        ReflectionTestUtils.setField(service, "projectService", new FakeProjectService());
        ReflectionTestUtils.setField(service, "logService", new RecordingLogService());
        ReflectionTestUtils.setField(service, "functionService", new FakeFunctionService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());
        ReflectionTestUtils.setField(service, "variableSourceResolver", resolver);
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", new NoOpRuntimeInvoker());
        ReflectionTestUtils.setField(service, "executionParameterBinder", new ExecutionParameterBinder());
        ReflectionTestUtils.setField(service, "compileService", new RuleCompileService() {
            @Override
            public CompileResult compilePreview(Long definitionId, String modelJson, String modelType) {
                return CompileResult.ok("decision = 1; decision", "QLEXPRESS");
            }
        });
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override
            public ResolvedFields resolveFields(Long definitionId, String modelJson, String modelType, Long projectId) {
                return new ResolvedFields(Collections.emptyList(), Collections.emptyList());
            }
        });

        RuleResult result = service.testExecutePreview(10L, "{\"script\":\"draft\"}", "SCRIPT",
                Collections.emptyMap());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertNotNull(resolver.requiredScriptNames);
        assertTrue(resolver.requiredScriptNames.isEmpty());
    }

    @Test
    public void publishedExecutionScansAndInstallsExplicitSourceStatus() {
        RuleExecuteService service = new RuleExecuteService();
        SourceStatusResolver resolver = new SourceStatusResolver();
        RuleDefinitionService definitionService = new FakeDefinitionService() {
            @Override
            public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
                RuleDefinitionInputField field = new RuleDefinitionInputField();
                field.setVarId(77L);
                field.setRefType("VARIABLE");
                field.setScriptName("apiScore");
                field.setFieldType("DOUBLE");
                return Collections.singletonList(field);
            }
        };
        ReflectionTestUtils.setField(service, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(service, "definitionService", definitionService);
        ReflectionTestUtils.setField(service, "projectService", new FakeProjectService());
        ReflectionTestUtils.setField(service, "logService", new RecordingLogService());
        ReflectionTestUtils.setField(service, "functionService", new FakeFunctionService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());
        ReflectionTestUtils.setField(service, "variableSourceResolver", resolver);
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", new NoOpRuntimeInvoker());
        ReflectionTestUtils.setField(service, "executionParameterBinder", new ExecutionParameterBinder());
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer());

        RulePublished published = new RulePublished();
        published.setDefinitionId(10L);
        published.setRuleCode("API_STATUS_RULE");
        published.setProjectCode("project_a");
        published.setVersion(1);
        published.setModelType("SCORECARD");
        published.setModelJson("{\"scoreItems\":[{\"leftOperand\":{\"kind\":\"REFERENCE\","
                + "\"refType\":\"VARIABLE\",\"refId\":77,\"code\":\"apiScore\"},"
                + "\"condOperator\":\"source_error\"}]}");
        published.setCompiledScript("sourceStatus(\"VARIABLE\", \"77\", \"OUTCOME\", \"ERROR\")");

        try {
            VariableResolveOptions options = VariableResolveOptions.defaults();
            options.setRequiredScriptNames(new LinkedHashSet<String>());
            RuleResult result = service.executePublishedWithOptions(
                    published, Collections.<String, Object>emptyMap(), 1L, "biz-app",
                    options, "CLIENT_SERVER").getResult();

            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(Boolean.TRUE, result.getResult());
            assertTrue(resolver.statusReferenceKeys.contains("VARIABLE:77"));
        } finally {
            RuntimeContextBridge.clear();
        }
    }

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
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer());

        RulePublished published = new RulePublished();
        published.setDefinitionId(10L);
        published.setRuleCode("RISK_RULE");
        published.setProjectCode("project_a");
        published.setVersion(3);
        published.setModelType("FLOW");
        published.setModelJson("{\"nodes\":[{\"id\":\"n1\",\"type\":\"task\",\"actionData\":[{"
                + "\"type\":\"assign\",\"target\":\"decision\","
                + "\"valueOperand\":{\"kind\":\"REFERENCE\",\"refId\":3,"
                + "\"refType\":\"MODEL_OUTPUT\",\"code\":\"facenox_antispoof.results\","
                + "\"value\":\"facenox_antispoof.results\"}}]}]}");
        published.setCompiledScript("decision = age >= 18 && externalScore >= 60 ? \"PASS\" : \"REJECT\";\ndecision");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("applicantId", "A001");
        params.put("age", "22");
        params.put("enabled", "false");
        params.put("tags", "[\"A\",\"B\"]");
        params.put("profile", "{\"level\":\"A\"}");
        params.put("requestMeta", Collections.<String, Object>singletonMap("channel", "APP"));
        ProjectAuthContext authContext = ProjectAuthContext.temporary(1L, "project_a", 11L,
                "BASIC_MAIN", ProjectAuthType.BASIC, 21L, "TOKEN_A", "GRACE");
        RuleExecuteService.ExecutionOutcome outcome = service.executePublishedWithOptions(
                published, params, 1L, "biz-app", VariableResolveOptions.defaults(), "CLIENT_SERVER", authContext);

        assertTrue(outcome.getResult().getErrorMessage(), outcome.getResult().isSuccess());
        assertEquals("PASS", outcome.getResult().getResult());
        assertEquals(Integer.valueOf(22), outcome.getExecuteParams().get("age"));
        assertEquals(Boolean.FALSE, outcome.getExecuteParams().get("enabled"));
        assertEquals(Arrays.asList("A", "B"), outcome.getExecuteParams().get("tags"));
        assertEquals("A", ((Map<?, ?>) outcome.getExecuteParams().get("profile")).get("level"));
        assertEquals(Integer.valueOf(80), outcome.getExecuteParams().get("externalScore"));
        assertEquals(Long.valueOf(1), resolver.projectId);
        assertNotNull(resolver.requiredScriptNames);
        assertTrue(resolver.requiredScriptNames.contains("externalScore"));
        assertTrue(resolver.requiredScriptNames.contains("facenox_antispoof.results"));
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
            RuleDefinitionInputField enabled = new RuleDefinitionInputField();
            enabled.setScriptName("enabled");
            enabled.setFieldType("BOOLEAN");
            RuleDefinitionInputField tags = new RuleDefinitionInputField();
            tags.setScriptName("tags");
            tags.setFieldType("ARRAY");
            RuleDefinitionInputField profile = new RuleDefinitionInputField();
            profile.setScriptName("profile");
            profile.setFieldType("OBJECT");
            return Arrays.asList(externalScore, age, enabled, tags, profile);
        }
    }

    private static class TerminationDefinitionService extends FakeDefinitionService {
        @Override
        public List<RuleDefinitionOutputField> listOutputFields(Long definitionId) {
            RuleDefinitionOutputField decision = new RuleDefinitionOutputField();
            decision.setScriptName("decision");
            RuleDefinitionOutputField notAssigned = new RuleDefinitionOutputField();
            notAssigned.setScriptName("notAssigned");
            return java.util.Arrays.asList(decision, notAssigned);
        }

        @Override
        public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
            return Collections.emptyList();
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

    private static class RecordingPreviewResolver extends VariableSourceResolver {
        private Set<String> requiredScriptNames;

        @Override
        public Map<String, Object> resolveInto(Long projectId, Map<String, Object> target,
                                               VariableResolveOptions options) {
            requiredScriptNames = options.getRequiredScriptNames();
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

    private static class SourceStatusResolver extends VariableSourceResolver {
        private Set<String> statusReferenceKeys;

        @Override
        public Map<String, Object> resolveInto(Long projectId, Map<String, Object> target,
                                               VariableResolveOptions options) {
            statusReferenceKeys = options.getStatusReferenceKeys();
            options.recordSourceState("VARIABLE", 77L, "OUTCOME", "ERROR");
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
        private String modelJson;

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
        public void enter(RuleDefinition definition, String projectCode,
                          Map<String, Object> values, Map<String, Object> originalInput,
                          boolean testMode, String modelJson) {
            this.values = values;
            this.originalInput = new LinkedHashMap<>(originalInput);
            this.modelJson = modelJson;
        }

        @Override
        public void enter(RuleDefinition definition, Long executionProjectId, String projectCode,
                          Map<String, Object> values, Map<String, Object> originalInput,
                          boolean testMode) {
            this.values = values;
            this.originalInput = new LinkedHashMap<>(originalInput);
        }

        @Override
        public void enter(RuleDefinition definition, Long executionProjectId, String projectCode,
                          Map<String, Object> values, Map<String, Object> originalInput,
                          boolean testMode, String modelJson) {
            this.values = values;
            this.originalInput = new LinkedHashMap<>(originalInput);
            this.modelJson = modelJson;
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
