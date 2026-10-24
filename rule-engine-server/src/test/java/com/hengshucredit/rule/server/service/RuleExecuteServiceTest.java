package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.ExecutionCapacityException;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
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
    public void workspaceAndPublishedRuleResolveReferencedModelOutputs() {
        RuleExecuteService service = newService();
        String model = "{\"rules\":[{\"conditionRoot\":{\"type\":\"leaf\",\"varCode\":\"score_f1.score\","
                + "\"_varId\":81,\"_refType\":\"MODEL_OUTPUT\",\"operator\":\">\",\"value\":\"0\"}}]}";
        ReflectionTestUtils.setField(service, "definitionService", new FakeDefinitionService());
        ReflectionTestUtils.setField(service, "projectService", new FakeProjectService());
        ReflectionTestUtils.setField(service, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(service, "executionParameterBinder", new ExecutionParameterBinder());
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer());
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", new NoOpRuntimeInvoker());
        ReflectionTestUtils.setField(service, "logService", new RecordingLogService());
        ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());
        ReflectionTestUtils.setField(service, "functionService", new FakeFunctionService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "variableSourceResolver", new VariableSourceResolver() {
            @Override public Map<String, Object> resolveInto(Long projectId, Map<String, Object> target,
                                                            VariableResolveOptions options) {
                if (options.getRequiredScriptNames().contains("score_f1.score")) {
                    target.put("score_f1", Map.of("score", 650));
                }
                return target;
            }
        });
        var target = new RuleTestTargetResolver.Target(new FakeDefinitionService().getById(10L), 1L,
                model, "RULE_SET", "score_f1.score", new RuleFieldAnalyzer.ResolvedFields(List.of(), List.of()),
                null, null, null);
        RuleResult workspace = service.testExecuteTarget(target, Map.of());
        assertTrue(workspace.getErrorMessage(), workspace.isSuccess());
        assertEquals(650, workspace.getResult());
        RulePublished published = new RulePublished();
        published.setDefinitionId(10L); published.setRuleCode("RISK_RULE"); published.setProjectCode("project_a");
        published.setModelType("RULE_SET"); published.setModelJson(model); published.setCompiledScript("score_f1.score");
        RuleResult result = service.executePublished(published, Map.of(), 1L, "test");
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(650, result.getResult());
    }

    @Test public void frozenGlobalTestKeepsTheSelectedProjectsBillingAttribution() {
        RuleExecuteService service = newService();
        RuleDefinitionService definitions = new FakeDefinitionService() {
            @Override public RuleDefinition getById(Serializable id) {
                RuleDefinition definition = super.getById(id);
                definition.setScope("GLOBAL"); definition.setProjectId(null);
                return definition;
            }
        };
        RecordingLogService logs = new RecordingLogService();
        RecordingBillingService billing = new RecordingBillingService();
        var snapshot = new ArtifactRuntimeSnapshotService.RuntimeSnapshot();
        ReflectionTestUtils.setField(snapshot, "modelType", "SCRIPT");
        ReflectionTestUtils.setField(snapshot, "modelJson", "{\"script\":\"frozenScore\"}");
        ReflectionTestUtils.setField(snapshot, "compiledScript", "frozenScore");
        ReflectionTestUtils.setField(service, "definitionService", definitions);
        ReflectionTestUtils.setField(service, "projectService", new FakeProjectService());
        ReflectionTestUtils.setField(service, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(service, "variableSourceResolver", new FrozenSnapshotResolver());
        ReflectionTestUtils.setField(service, "executionParameterBinder", new ExecutionParameterBinder());
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer());
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", new NoOpRuntimeInvoker());
        ReflectionTestUtils.setField(service, "logService", logs);
        ReflectionTestUtils.setField(service, "billingService", billing);
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        RulePublished published = new RulePublished();
        published.setDefinitionId(10L); published.setVersion(4); published.setRevisionId(33L); published.setArtifactId(501L);
        published.setArtifactDigest("frozen"); published.setRuleCode("RISK_RULE"); published.setProjectCode("bound_project");
        published.setModelType("SCRIPT"); published.setModelJson(snapshot.getModelJson()); published.setCompiledScript("frozenScore");
        var identity = new com.hengshucredit.rule.model.dto.RuleTestExecutionIdentity();
        identity.setDefinitionId("10"); identity.setSourceType("REVISION"); identity.setSourceId("33");
        identity.setVersionNo(4); identity.setRevisionId("33"); identity.setArtifactDigest("frozen");
        var target = new RuleTestTargetResolver.Target(definitions.getById(10L), 7L, snapshot.getModelJson(), "SCRIPT", "frozenScore",
                new RuleFieldAnalyzer.ResolvedFields(List.of(), List.of()), published, snapshot, identity);
        RuleResult result = service.testExecuteTarget(target, Map.of());
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(1, billing.count);
        assertEquals(Long.valueOf(7L), logs.saved.getExecutionProjectId());
        assertEquals(Integer.valueOf(4), logs.saved.getRuleVersion());
        assertEquals(Long.valueOf(33L), logs.saved.getRevisionId());
        assertEquals("frozen", logs.saved.getArtifactDigest());
        assertEquals("frozen", result.getArtifactDigest());
        assertNotNull("关联全局规则的测试必须保留所选项目的真实计费归属", billing.authContext);
        assertEquals(Long.valueOf(7L), billing.authContext.getProjectId());
        assertEquals("bound_project", billing.authContext.getProjectCode());
    }

    @Test public void revisionTestLogKeepsSelectedBusinessVersionAttribution() {
        RuleExecuteService service = newService();
        RecordingLogService logs = new RecordingLogService();
        RecordingBillingService billing = new RecordingBillingService();
        ReflectionTestUtils.setField(service, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(service, "definitionService", new FakeDefinitionService());
        ReflectionTestUtils.setField(service, "projectService", new FakeProjectService());
        ReflectionTestUtils.setField(service, "logService", logs);
        ReflectionTestUtils.setField(service, "billingService", billing);
        ReflectionTestUtils.setField(service, "functionService", new FakeFunctionService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "variableSourceResolver", new PassThroughVariableResolver());
        ReflectionTestUtils.setField(service, "executionParameterBinder", new ExecutionParameterBinder());
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", new NoOpRuntimeInvoker());
        var identity = new com.hengshucredit.rule.model.dto.RuleTestExecutionIdentity();
        identity.setDefinitionId("10"); identity.setSourceType("VERSION"); identity.setSourceId("90");
        identity.setRevisionId("33"); identity.setVersionNo(4); identity.setArtifactDigest("frozen");
        var target = new RuleTestTargetResolver.Target(new FakeDefinitionService().getById(10L), 1L,
                "{\"script\":\"1\"}", "SCRIPT", "1",
                new RuleFieldAnalyzer.ResolvedFields(List.of(), List.of()), null, null, identity);

        RuleResult result = service.testExecuteTarget(target, Map.of());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(Integer.valueOf(4), logs.saved.getRuleVersion());
        assertEquals(Long.valueOf(33L), logs.saved.getRevisionId());
        assertEquals("frozen", logs.saved.getArtifactDigest());
        assertEquals("frozen", result.getArtifactDigest());
    }

    @Test public void unsavedPreviewCollectsItsOwnOutputFieldsInsteadOfTheSavedProjection() {
        QLExpressEngine engine = new QLExpressEngine();
        RuleDefinitionOutputField oldOutput = new RuleDefinitionOutputField();
        oldOutput.setScriptName("oldScore");
        RuleDefinitionOutputField previewOutput = new RuleDefinitionOutputField();
        previewOutput.setScriptName("newScore");
        RuleDefinitionService definitions = new FakeDefinitionService() {
            @Override public RuleDefinition getById(Serializable id) {
                RuleDefinition definition = super.getById(id);
                definition.setModelType("SCORE");
                return definition;
            }
            @Override public List<RuleDefinitionOutputField> listOutputFields(Long id) {
                return List.of(oldOutput);
            }
        };
        FakeProjectService projects = new FakeProjectService();
        PassThroughVariableResolver resolver = new PassThroughVariableResolver();
        ExecutionParameterBinder binder = new ExecutionParameterBinder();
        RuleRuntimeInvoker runtime = new RuleRuntimeInvoker();
        ReflectionTestUtils.setField(runtime, "definitionService", definitions);
        ReflectionTestUtils.setField(runtime, "projectService", projects);
        ReflectionTestUtils.setField(runtime, "variableSourceResolver", resolver);
        ReflectionTestUtils.setField(runtime, "qlExpressEngine", engine);
        ReflectionTestUtils.setField(runtime, "executionParameterBinder", binder);
        RuleExecuteService service = newService();
        ReflectionTestUtils.setField(service, "qlExpressEngine", engine);
        ReflectionTestUtils.setField(service, "definitionService", definitions);
        ReflectionTestUtils.setField(service, "projectService", projects);
        ReflectionTestUtils.setField(service, "variableSourceResolver", resolver);
        ReflectionTestUtils.setField(service, "executionParameterBinder", binder);
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", runtime);
        ReflectionTestUtils.setField(service, "logService", new RecordingLogService());
        ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());
        ReflectionTestUtils.setField(service, "functionService", new FakeFunctionService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "compileService", new RuleCompileService() {
            @Override public CompileResult compilePreview(Long id, String model, String type) {
                return new com.hengshucredit.rule.core.compiler.ScorecardCompiler().compile(model);
            }
        });
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override public ResolvedFields resolveFields(Long id, String model, String type, Long projectId) {
                return new ResolvedFields(List.of(), List.of(previewOutput));
            }
            @Override public List<RuleDefinitionInputField> resolveDirectModelInputFields(String model, String type, Long projectId) {
                return List.of();
            }
        });
        RuleResult result = service.testExecutePreview(10L,
                "{\"initialScore\":5,\"resultVar\":{\"varCode\":\"newScore\"},\"scoreItems\":[]}",
                "SCORE", Map.of());
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals("未保存画布输出不能被旧输出契约覆盖", Map.of("newScore", 5.0), result.getResult());
    }

    @Test public void publishedScorecardReturnsTheSameDeclaredOutputsWithTraceEnabledOrDisabled() {
        QLExpressEngine engine = new QLExpressEngine();
        RuleExecuteService service = newService();
        TerminationDefinitionService definitions = new TerminationDefinitionService();
        FakeProjectService projects = new FakeProjectService();
        PassThroughVariableResolver resolver = new PassThroughVariableResolver();
        ExecutionParameterBinder binder = new ExecutionParameterBinder();
        RuleRuntimeInvoker runtime = new RuleRuntimeInvoker();
        ReflectionTestUtils.setField(runtime, "definitionService", definitions);
        ReflectionTestUtils.setField(runtime, "projectService", projects);
        ReflectionTestUtils.setField(runtime, "variableSourceResolver", resolver);
        ReflectionTestUtils.setField(runtime, "qlExpressEngine", engine);
        ReflectionTestUtils.setField(runtime, "executionParameterBinder", binder);
        ReflectionTestUtils.setField(service, "qlExpressEngine", engine);
        ReflectionTestUtils.setField(service, "definitionService", definitions);
        ReflectionTestUtils.setField(service, "projectService", projects);
        ReflectionTestUtils.setField(service, "variableSourceResolver", resolver);
        ReflectionTestUtils.setField(service, "executionParameterBinder", binder);
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", runtime);
        ReflectionTestUtils.setField(service, "logService", new RecordingLogService());
        ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());
        ReflectionTestUtils.setField(service, "functionService", new FakeFunctionService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        String model = "{\"initialScore\":0,\"resultVar\":{\"varCode\":\"decision\"},\"scoreItems\":[{\"condVar\":\"age\",\"condOperator\":\">=\",\"condValue\":\"18\",\"condVarType\":\"NUMBER\",\"score\":5}]}";
        CompileResult compiled = new com.hengshucredit.rule.core.compiler.ScorecardCompiler().compile(model);
        assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
        RulePublished published = new RulePublished();
        published.setDefinitionId(10L); published.setRuleCode("RISK_RULE"); published.setProjectCode("project_a");
        published.setVersion(1); published.setModelType("SCORECARD"); published.setModelJson(model);
        published.setCompiledScript(compiled.getCompiledScript());
        for (boolean trace : new boolean[]{false, true}) {
            RuleResult result = service.executePublished(published, Map.of("age", 25), 1L, "PUBLIC_API", null, trace, trace);
            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals("公开无追踪执行不能丢失设计器评分输出", Double.valueOf(5), ((Map<?, ?>) result.getResult()).get("decision"));
        }
    }

    @Test
    public void deletedRuleCannotUseStoredOrUnsavedPreviewContent() {
        for (boolean preview : new boolean[]{false, true}) {
            int[] reads = {0};
            RuleExecuteService service = deletionGuardService(-1, reads);
            RuleResult result = preview ? service.testExecutePreview(10L, "{}", "SCORECARD", Map.of())
                    : service.testExecute(10L, Map.of());
            assertFalse(result.isSuccess());
            assertEquals("规则已删除，无法执行测试", result.getErrorMessage());
            assertEquals("旧页面不能继续加载内容或编译新草稿", 0, reads[0]);
        }
    }

    @Test
    public void deletionGuardKeepsDraftAndOfflineRuleTestingAvailable() {
        for (Integer status : java.util.Arrays.asList(null, 0, 1, 2)) {
            int[] reads = {0};
            RuleExecuteService service = deletionGuardService(status, reads);
            assertEquals("规则尚未编译成功，请先编译", service.testExecute(10L, Map.of()).getErrorMessage());
            assertEquals(1, reads[0]);
            assertEquals("预览编译已调用", service.testExecutePreview(10L, "{}", "SCORECARD", Map.of()).getErrorMessage());
            assertEquals(2, reads[0]);
        }
    }

    private RuleExecuteService deletionGuardService(Integer status, int[] contentReads) {
        RuleExecuteService service = newService();
        ReflectionTestUtils.setField(service, "definitionService", new RuleDefinitionService() {
            @Override public RuleDefinition getById(Serializable id) {
                RuleDefinition definition = new RuleDefinition();
                definition.setId(10L); definition.setStatus(status);
                return definition;
            }
            @Override public RuleDefinitionContent getContent(Long id) { contentReads[0]++; return null; }
        });
        ReflectionTestUtils.setField(service, "compileService", new RuleCompileService() {
            @Override public CompileResult compilePreview(Long id, String json, String type) {
                contentReads[0]++;
                return CompileResult.fail("预览编译已调用");
            }
        });
        return service;
    }

    @Test
    public void testAndPublishedExecutionPreserveTypedCapacityResource() {
        for (ExecutionCapacityException.Resource resource : ExecutionCapacityException.Resource.values()) {
            RuleExecuteService service = capacityFailureService(new IllegalStateException("wrapped",
                    new ExecutionCapacityException(resource, "capacity unavailable")));
            try {
                RuleResult tested = service.testExecute(10L, Collections.emptyMap());
                assertFalse(tested.isSuccess());
                assertEquals("THROTTLED", tested.getExecutionStatus());
                assertEquals(resource.name(), tested.getCapacityResource());
                assertEquals(Long.valueOf(1000), tested.getRetryAfterMs());
                RuleResult published = service.executePublished(capacityPublished(), Collections.emptyMap(), 1L, "test");
                assertFalse(published.isSuccess());
                assertEquals("THROTTLED", published.getExecutionStatus());
                assertEquals(resource.name(), published.getCapacityResource());
            } finally {
                RuntimeContextBridge.clear();
            }
        }
    }

    @Test
    public void ordinaryRejectionMessageDoesNotSetThrottledExecutionStatus() {
        RuleExecuteService service = capacityFailureService(new java.util.concurrent.RejectedExecutionException("PMML 模型加载并发已达上限"));
        try {
            RuleResult result = service.executePublished(capacityPublished(), Collections.emptyMap(), 1L, "test");
            assertFalse(result.isSuccess());
            org.junit.Assert.assertNull(result.getExecutionStatus());
            org.junit.Assert.assertNull(result.getCapacityResource());
        } finally {
            RuntimeContextBridge.clear();
        }
    }

    private RulePublished capacityPublished() {
        RulePublished published = new RulePublished();
        published.setDefinitionId(10L); published.setRuleCode("RISK_RULE");
        published.setProjectCode("project_a"); published.setVersion(1);
        published.setModelType("SCRIPT"); published.setModelJson("{}");
        published.setCompiledScript("return externalScore;");
        return published;
    }

    private RuleExecuteService capacityFailureService(RuntimeException failure) {
        RuleExecuteService service = newService();
        ReflectionTestUtils.setField(service, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(service, "definitionService", new FakeDefinitionService() {
            @Override public RuleDefinitionContent getContent(Long id) {
                RuleDefinitionContent content = new RuleDefinitionContent();
                content.setCompileStatus(1); content.setModelJson("{}"); content.setCompiledScript("return externalScore;");
                return content;
            }
        });
        ReflectionTestUtils.setField(service, "projectService", new FakeProjectService());
        ReflectionTestUtils.setField(service, "functionService", new FakeFunctionService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "logService", new RecordingLogService());
        ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", new NoOpRuntimeInvoker());
        ReflectionTestUtils.setField(service, "executionParameterBinder", new ExecutionParameterBinder());
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer());
        ReflectionTestUtils.setField(service, "variableSourceResolver", new VariableSourceResolver() {
            @Override public Map<String, Object> resolveInto(Long id, Map<String, Object> target, VariableResolveOptions options) {
                throw failure;
            }
        });
        return service;
    }

    @Test
    public void publishedFlowOnlyResolvesExternalSourceWhenItsBranchReadsTheValue() {
        RuleExecuteService service = newService();
        ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot = new ArtifactRuntimeSnapshotService.RuntimeSnapshot();
        ReflectionTestUtils.setField(snapshot, "compiledScript", "if (reject) { return 0; }\nreturn externalScore + externalScore;");
        ReflectionTestUtils.setField(snapshot, "modelType", "FLOW");
        ReflectionTestUtils.setField(snapshot, "modelJson", "{\"nodes\":[],\"edges\":[]}");
        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setScriptName("reject"); input.setFieldType("BOOLEAN");
        snapshot.getInputFields().add(input);
        RuleDefinitionInputField external = new RuleDefinitionInputField();
        external.setScriptName("externalScore"); external.setVarId(8L); external.setRefType("VARIABLE");
        int[] calls = {0};
        ReflectionTestUtils.setField(service, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(service, "definitionService", new FakeDefinitionService());
        ReflectionTestUtils.setField(service, "projectService", new FakeProjectService());
        ReflectionTestUtils.setField(service, "logService", new RecordingLogService());
        ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", new NoOpRuntimeInvoker());
        ReflectionTestUtils.setField(service, "executionParameterBinder", new ExecutionParameterBinder());
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override public List<RuleDefinitionInputField> extractFrozenModelInputFields(String json, String type,
                    ArtifactRuntimeSnapshotService.RuntimeSnapshot frozen) { return List.of(external); }
        });
        ReflectionTestUtils.setField(service, "artifactRuntimeSnapshotService", new ArtifactRuntimeSnapshotService() {
            @Override public RuntimeSnapshot load(Long artifact, Long definition, Long project) { return snapshot; }
        });
        ReflectionTestUtils.setField(service, "variableSourceResolver", new VariableSourceResolver() {
            @Override public Map<String, Object> resolveIntoSnapshot(List<RuleVariable> variables, List<RuleModel> models,
                    List<RuleFunction> functions, Map<String, Object> values, VariableResolveOptions options) {
                if (options.getRequiredScriptNames().contains("externalScore")) {
                    calls[0]++;
                    values.put("externalScore", 88);
                }
                return values;
            }
        });
        RulePublished published = new RulePublished();
        published.setDefinitionId(10L); published.setArtifactId(99L); published.setRuleCode("BRANCHED");
        published.setProjectCode("project_a"); published.setModelType("FLOW"); published.setVersion(1);
        try {
            RuleResult rejected = service.executePublished(published, Map.of("reject", true), 1L, "test");
            assertTrue(rejected.getErrorMessage(), rejected.isSuccess());
            assertEquals(0, ((Number) rejected.getResult()).intValue());
            assertEquals("提前拒绝不能预先请求未走到分支的外数", 0, calls[0]);
            RuleResult accepted = service.executePublished(published, Map.of("reject", false), 1L, "test");
            assertTrue(accepted.getErrorMessage(), accepted.isSuccess());
            assertEquals(176, ((Number) accepted.getResult()).intValue());
            assertEquals("相同字段多次读取只解析一次", 1, calls[0]);
        } finally {
            RuntimeContextBridge.clear();
        }
    }

    @Test
    public void publishedScriptUsesFrozenFieldsWithoutReenteringLiveAnalysis() {
        RuleExecuteService service = newService();
        QLExpressEngine engine = new QLExpressEngine();
        String script = "return frozenScore;";
        QLExpressEngine.PreparedScript prepared = engine.prepare(script);
        ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot = new ArtifactRuntimeSnapshotService.RuntimeSnapshot();
        ReflectionTestUtils.setField(snapshot, "compiledScript", script);
        ReflectionTestUtils.setField(snapshot, "modelType", "SCRIPT");
        ReflectionTestUtils.setField(snapshot, "modelJson", "{\"script\":\"return frozenScore;\",\"scriptVarRefs\":[{\"varId\":7,\"refType\":\"VARIABLE\",\"refCode\":\"frozenScore\"}]}");
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setVarId(7L); field.setRefType("VARIABLE"); field.setScriptName("frozenScore"); field.setFieldType("NUMBER");
        snapshot.getInputFields().add(field);
        ReflectionTestUtils.setField(service, "qlExpressEngine", engine);
        ReflectionTestUtils.setField(service, "definitionService", new FakeDefinitionService());
        ReflectionTestUtils.setField(service, "projectService", new FakeProjectService());
        ReflectionTestUtils.setField(service, "logService", new RecordingLogService());
        ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", new NoOpRuntimeInvoker());
        ReflectionTestUtils.setField(service, "executionParameterBinder", new ExecutionParameterBinder());
        ReflectionTestUtils.setField(service, "variableSourceResolver", new VariableSourceResolver() {
            @Override public Map<String, Object> resolveIntoSnapshot(List<RuleVariable> variables, List<RuleModel> models,
                    List<RuleFunction> functions, Map<String, Object> values, VariableResolveOptions options) {
                assertEquals(Set.of("frozenScore"), options.getRequiredScriptNames());
                return values;
            }
        });
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override public List<RuleDefinitionInputField> extractDirectModelInputFields(String json, String type) {
                throw new AssertionError("published scripts must not parse source or read live references");
            }
        });
        ReflectionTestUtils.setField(service, "artifactRuntimeSnapshotService", new ArtifactRuntimeSnapshotService() {
            @Override public RuntimeSnapshot load(Long artifact, Long definition, Long project) { return snapshot; }
        });
        RulePublished published = new RulePublished();
        published.setDefinitionId(10L); published.setArtifactId(99L); published.setRuleCode("FROZEN");
        published.setProjectCode("project_a"); published.setModelType("SCRIPT"); published.setVersion(1);
        try {
            for (int i = 0; i < 2; i++) {
                RuleResult result = service.executePublished(published, Map.of("frozenScore", "91"), 1L, "test");
                assertTrue(result.getErrorMessage(), result.isSuccess());
                assertEquals(91d, result.getResult());
                assertSame(prepared, engine.prepare(script));
            }
        } finally {
            RuntimeContextBridge.clear();
        }
    }

    @Test
    public void publishedExecutionAssemblesMappedObjectFieldAndKeepsExplicitTargetPriority() {
        RuleExecuteService service = newService();
        RuleDefinitionInputField age = new RuleDefinitionInputField();
        age.setVarId(9L);
        age.setRefType("VARIABLE");
        age.setScriptName("age");
        age.setFieldType("NUMBER");
        RuleDefinitionInputField requestAge = new RuleDefinitionInputField();
        requestAge.setVarId(30L);
        requestAge.setRefType("DATA_OBJECT");
        requestAge.setScriptName("request.age");
        requestAge.setFieldType("NUMBER");

        RuleVariable ageVariable = new RuleVariable();
        ageVariable.setId(9L);
        ageVariable.setVarCode("age");
        ageVariable.setVarLabel("年龄");
        ageVariable.setScriptName("age");
        ageVariable.setVarType("NUMBER");
        ageVariable.setVarSource("INPUT");
        ageVariable.setStatus(1);
        RuleDataObjectField mappedField = new RuleDataObjectField();
        mappedField.setId(30L);
        mappedField.setRefVariableId(9L);
        DataObjectFieldReferenceResolver delegate =
                new DataObjectFieldReferenceResolver();
        DataObjectFieldReferenceResolver.ReferencePlan plan =
                delegate.resolveSnapshot(
                        Collections.singletonList(requestAge),
                        Collections.singletonList(mappedField),
                        Collections.singletonList(ageVariable));

        RuleDefinitionService definitionService = new FakeDefinitionService() {
            @Override
            public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
                return Collections.singletonList(age);
            }
        };
        RuleFieldAnalyzer analyzer = new RuleFieldAnalyzer() {
            @Override
            public List<RuleDefinitionInputField> extractDirectModelInputFields(
                    String modelJson, String modelType) {
                return Collections.singletonList(requestAge);
            }
        };
        ReflectionTestUtils.setField(service, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(service, "definitionService", definitionService);
        ReflectionTestUtils.setField(service, "projectService", new FakeProjectService());
        ReflectionTestUtils.setField(service, "logService", new RecordingLogService());
        ReflectionTestUtils.setField(service, "functionService", new FakeFunctionService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());
        ReflectionTestUtils.setField(service, "variableSourceResolver", new PassThroughVariableResolver());
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", new NoOpRuntimeInvoker());
        ReflectionTestUtils.setField(service, "executionParameterBinder", new ExecutionParameterBinder());
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", analyzer);
        ReflectionTestUtils.setField(service, "dataObjectFieldReferenceResolver",
                new DataObjectFieldReferenceResolver() {
                    @Override
                    public ReferencePlan resolveLive(List<RuleDefinitionInputField> directFields) {
                        return plan;
                    }
                });

        RulePublished published = new RulePublished();
        published.setDefinitionId(10L);
        published.setRuleCode("MAPPED_AGE");
        published.setProjectCode("project_a");
        published.setVersion(1);
        published.setModelType("SCRIPT");
        published.setModelJson("{\"script\":\"request.age\"}");
        published.setCompiledScript("request.age");

        RuleResult assembled = service.executePublished(
                published, Collections.<String, Object>singletonMap("age", "18"),
                1L, "biz-app");
        Map<String, Object> explicitRequest = new LinkedHashMap<>();
        explicitRequest.put("age", "21");
        Map<String, Object> explicitInput = new LinkedHashMap<>();
        explicitInput.put("age", "18");
        explicitInput.put("request", explicitRequest);
        RuleResult explicit = service.executePublished(
                published, explicitInput, 1L, "biz-app");

        assertTrue(assembled.getErrorMessage(), assembled.isSuccess());
        assertEquals(Double.valueOf(18D), assembled.getResult());
        assertTrue(explicit.getErrorMessage(), explicit.isSuccess());
        assertEquals(Double.valueOf(21D), explicit.getResult());
    }

    @Test
    public void allRulesTerminationReturnsRootOutputContractAsSuccess() {
        RuleExecuteService service = newService();
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
        published.setRevisionId(33L);
        published.setArtifactDigest("artifact-digest");
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
        assertEquals(Long.valueOf(33L), logService.saved.getRevisionId());
        assertEquals("artifact-digest", logService.saved.getArtifactDigest());

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
        RuleExecuteService service = newService();
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
    public void runtimeResolutionIncludesDerivedReferencesWithoutExposingThemAsInputFields() {
        RuleExecuteService service = newService();
        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setScriptName("idcard_no");
        input.setFieldType("STRING");

        VariableResolveOptions options = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "withInputFields", VariableResolveOptions.defaults(),
                Collections.singletonList(input),
                "{\"leftOperand\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":37,\"code\":\"age\"}}",
                Collections.emptyList());

        assertTrue(options.getRequiredScriptNames().contains("idcard_no"));
        assertTrue(options.getRequiredScriptNames().contains("age"));
    }

    @Test
    public void previewUsesRequestedModelTypeWhenAnalyzingDirectFields() {
        RuleExecuteService service = newService();
        String[] analyzedType = new String[1];
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
                return CompileResult.ok("1", "QLEXPRESS");
            }
        });
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override
            public ResolvedFields resolveFields(Long definitionId, String modelJson,
                                                String modelType, Long projectId) {
                return new ResolvedFields(Collections.emptyList(), Collections.emptyList());
            }

            @Override
            public List<RuleDefinitionInputField> extractDirectModelInputFields(
                    String modelJson, String modelType) {
                analyzedType[0] = modelType;
                return Collections.emptyList();
            }
        });

        RuleResult result = service.testExecutePreview(
                10L, "{\"nodes\":[]}", "FLOW", Collections.emptyMap());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals("FLOW", analyzedType[0]);
    }

    @Test
    public void previewExecutionWithNoInputsRequiresNoSourceVariables() {
        RuleExecuteService service = newService();
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
    public void boundGlobalRulePreviewUsesSelectedProjectForRuntimeSourcesAndLogs() {
        RuleExecuteService service = newService();
        RecordingPreviewResolver resolver = new RecordingPreviewResolver();
        RecordingLogService logService = new RecordingLogService();
        RecordingBillingService billingService = new RecordingBillingService();
        NoOpRuntimeInvoker runtimeInvoker = new NoOpRuntimeInvoker();
        RuleDefinitionService definitionService = new FakeDefinitionService() {
            @Override
            public RuleDefinition getById(Serializable id) {
                RuleDefinition definition = super.getById(id);
                definition.setProjectId(null);
                definition.setScope("GLOBAL");
                return definition;
            }

            @Override
            public boolean isDefinitionAvailableInProject(Long definitionId,
                                                          Long projectId) {
                return Long.valueOf(10L).equals(definitionId)
                        && Long.valueOf(7L).equals(projectId);
            }
        };
        RuleProjectService projectService = new FakeProjectService() {
            @Override
            public RuleProject getById(Serializable id) {
                RuleProject project = new RuleProject();
                project.setId(7L);
                project.setProjectCode("bound_project");
                return project;
            }
        };

        ReflectionTestUtils.setField(service, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(service, "definitionService", definitionService);
        ReflectionTestUtils.setField(service, "projectService", projectService);
        ReflectionTestUtils.setField(service, "logService", logService);
        ReflectionTestUtils.setField(service, "functionService", new FakeFunctionService());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        ReflectionTestUtils.setField(service, "billingService", billingService);
        ReflectionTestUtils.setField(service, "variableSourceResolver", resolver);
        ReflectionTestUtils.setField(service, "runtimeRuleInvoker", runtimeInvoker);
        ReflectionTestUtils.setField(service, "executionParameterBinder", new ExecutionParameterBinder());
        ReflectionTestUtils.setField(service, "compileService", new RuleCompileService() {
            @Override
            public CompileResult compilePreview(Long definitionId, String modelJson,
                                                String modelType) {
                return CompileResult.ok("1", "QLEXPRESS");
            }
        });
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override
            public ResolvedFields resolveFields(Long definitionId, String modelJson,
                                                String modelType, Long projectId) {
                assertEquals(Long.valueOf(7L), projectId);
                return new ResolvedFields(Collections.emptyList(), Collections.emptyList());
            }
        });

        RuleResult result = service.testExecutePreview(
                10L, "{\"script\":\"1\"}", "SCRIPT",
                Collections.emptyMap(), 7L);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(Long.valueOf(7L), resolver.projectId);
        assertEquals(Long.valueOf(7L), runtimeInvoker.executionProjectId);
        assertEquals("bound_project", logService.saved.getProjectCode());
        assertEquals(Long.valueOf(7L), billingService.authContext.getProjectId());
    }

    @Test
    public void testExecutionRejectsProjectThatDoesNotOwnOrBindRule() {
        RuleExecuteService service = newService();
        ReflectionTestUtils.setField(service, "definitionService",
                new FakeDefinitionService() {
                    @Override
                    public boolean isDefinitionAvailableInProject(
                            Long definitionId, Long projectId) {
                        return false;
                    }
                });

        RuleResult result = service.testExecute(
                10L, Collections.emptyMap(), 99L);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("未关联"));
    }

    @Test
    public void publishedExecutionScansAndInstallsExplicitSourceStatus() {
        RuleExecuteService service = newService();
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
    public void executePublishedFlowResolvesReadSourcesAndPreservesBindingAndLogging() {
        RuleExecuteService service = newService();
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
        assertFalse("未读取的模型输出不能触发解析", resolver.requiredScriptNames.contains("facenox_antispoof.results"));
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

        int beforeBilling = billingService.count;
        service.executePublishedWithOptions(published, params, 1L, "replay",
                VariableResolveOptions.defaults(), "REPLAY", null,
                true, false, false);
        assertEquals("回溯执行不能写入新的规则日志", 1, logService.saveCount);
        assertEquals("回溯执行不能产生计费记录", beforeBilling, billingService.count);
    }

    @Test
    public void publishedArtifactExecutesOnlyFrozenInputsAndDependencies() {
        RuleExecuteService service = newService();
        FrozenSnapshotResolver resolver = new FrozenSnapshotResolver();
        ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot =
                new ArtifactRuntimeSnapshotService.RuntimeSnapshot();
        RuleDefinitionInputField frozenInput = new RuleDefinitionInputField();
        frozenInput.setScriptName("frozenScore");
        frozenInput.setFieldType("INTEGER");
        snapshot.getInputFields().add(frozenInput);
        RuleVariable frozenVariable = new RuleVariable();
        frozenVariable.setId(88L);
        frozenVariable.setScriptName("frozenScore");
        snapshot.getVariables().add(frozenVariable);

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
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer());
        ReflectionTestUtils.setField(service, "artifactRuntimeSnapshotService",
                new ArtifactRuntimeSnapshotService() {
                    @Override
                    public RuntimeSnapshot load(Long artifactId, Long definitionId, Long executionProjectId) {
                        assertEquals(Long.valueOf(501L), artifactId);
                        assertEquals(Long.valueOf(10L), definitionId);
                        assertEquals(Long.valueOf(1L), executionProjectId);
                        return snapshot;
                    }
                });

        RulePublished published = new RulePublished();
        published.setDefinitionId(10L);
        published.setArtifactId(501L);
        published.setRuleCode("FROZEN_RULE");
        published.setProjectCode("project_a");
        published.setVersion(1);
        published.setModelType("SCRIPT");
        published.setModelJson("{\"script\":\"frozenScore\"}");
        published.setCompiledScript("frozenScore");

        RuleResult result = service.executePublished(
                published, Collections.<String, Object>emptyMap(), 1L, "biz-app");

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(Integer.valueOf(91), result.getResult());
        assertTrue(resolver.snapshotCalled);
        assertFalse(resolver.currentCalled);
        assertSame(snapshot.getVariables(), resolver.variables);
    }

    private static RuleExecuteService newService() {
        RuleExecuteService service = new RuleExecuteService();
        ReflectionTestUtils.setField(service, "namespaceGuard", new RuleReferenceNamespaceGuard() {
            @Override public void requireLocalWorkspace(RuleDefinition definition, RuleDefinitionContent content) { }
            @Override public void requireLocalNamespace(RuleDefinition definition, String sourceType, Long sourceId) { }
        });
        return service;
    }

    private static class FakeDefinitionService extends RuleDefinitionService {
        @Override
        public List<RuleDefinitionOutputField> listOutputFields(Long definitionId) {
            return Collections.emptyList();
        }

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
            Map<String, Object> requestMeta = new LinkedHashMap<>();
            if (target.get("requestMeta") instanceof Map<?, ?> raw) {
                raw.forEach((key, value) -> {
                    if (key != null) requestMeta.put(String.valueOf(key), value);
                });
            }
            requestMeta.put("channel", "DERIVED");
            target.put("requestMeta", requestMeta);
            return target;
        }
    }

    private static class RecordingPreviewResolver extends VariableSourceResolver {
        private Long projectId;
        private Set<String> requiredScriptNames;

        @Override
        public Map<String, Object> resolveInto(Long projectId, Map<String, Object> target,
                                               VariableResolveOptions options) {
            this.projectId = projectId;
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

    private static class FrozenSnapshotResolver extends VariableSourceResolver {
        private boolean currentCalled;
        private boolean snapshotCalled;
        private List<RuleVariable> variables;

        @Override
        public Map<String, Object> resolveInto(Long projectId, Map<String, Object> target,
                                               VariableResolveOptions options) {
            currentCalled = true;
            throw new AssertionError("已发布制品不得读取当前项目变量");
        }

        @Override
        public Map<String, Object> resolveIntoSnapshot(List<RuleVariable> variables,
                                                       List<RuleModel> models,
                                                       List<RuleFunction> functions,
                                                       Map<String, Object> target,
                                                       VariableResolveOptions options) {
            snapshotCalled = true;
            this.variables = variables;
            target.put("frozenScore", 91);
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
        private int count;

        @Override
        public void recordEngineExecution(RuleDefinition definition, boolean success, Long executeTimeMs,
                                          String errorMessage, ProjectAuthContext authContext) {
            count++;
            this.authContext = authContext;
            RuleResult ignored = new RuleResult();
            ignored.setSuccess(success);
        }
    }

    private static class NoOpRuntimeInvoker extends RuleRuntimeInvoker {
        private Map<String, Object> values;
        private Map<String, Object> originalInput;
        private String modelJson;
        private Long executionProjectId;

        @Override
        public void enterTest(RuleDefinition definition, Long executionProjectId, String projectCode,
                              Map<String, Object> values, Map<String, Object> originalInput, String modelJson,
                              List<RuleDefinitionOutputField> outputFields) {
            enter(definition, executionProjectId, projectCode, values, originalInput, true, modelJson);
        }

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
            this.executionProjectId = executionProjectId;
            this.modelJson = modelJson;
        }

        @Override
        public void enterArtifact(RuleDefinition definition, Long executionProjectId, String projectCode,
                                  Map<String, Object> values, Map<String, Object> originalInput,
                                  boolean testMode, String modelJson,
                                  ArtifactRuntimeSnapshotService.RuntimeSnapshot runtimeSnapshot) {
            this.values = values;
            this.originalInput = new LinkedHashMap<>(originalInput);
            this.modelJson = modelJson;
            this.executionProjectId = executionProjectId;
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
