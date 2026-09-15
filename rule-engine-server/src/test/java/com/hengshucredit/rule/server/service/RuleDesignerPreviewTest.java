package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.core.compiler.ScriptPassthroughCompiler;
import com.hengshucredit.rule.model.dto.RuleDesignerCompileRequest;
import com.hengshucredit.rule.model.dto.RuleDesignerCompileResponse;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.enums.RuleDraftSourceType;
import com.hengshucredit.rule.server.artifact.RuleDependencyClosureService;
import com.hengshucredit.rule.server.artifact.RulePreflightValidationService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.Assert.*;

public class RuleDesignerPreviewTest {
    @Test
    public void currentPayloadDeterminesDependenciesWithoutLoadingPersistedRoot() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(30L);
        definition.setProjectId(4L);
        definition.setModelType("SCRIPT");
        RulePreflightValidationService preflight = new RulePreflightValidationService() {
            @Override protected RuleDefinition loadDefinition(Long id) { return definition; }
            @Override protected RuleRevision loadRevision(Long id) { throw new AssertionError("preview read stored root"); }
            @Override protected RuleRevision loadPreviousPublishedRevision(RuleRevision revision) { return null; }
            @Override protected RuleReferenceIntegrityService.AuditReport auditReferences(RuleDefinition ignored, RuleRevision revision) {
                return new RuleReferenceIntegrityService.AuditReport(30L, List.of());
            }
            @Override protected RuleFieldAnalyzer.ResolvedFields resolveFields(RuleDefinition ignored, RuleRevision revision) {
                return new RuleFieldAnalyzer.ResolvedFields(List.of(), List.of());
            }
            @Override protected CompileResult compile(RuleDefinition ignored, RuleRevision revision) {
                return new ScriptPassthroughCompiler().compile(revision.getModelJson());
            }
        };
        RuleDependencyClosureService dependencies = new RuleDependencyClosureService() {
            @Override protected RuleRevision loadRevision(Long id) { throw new AssertionError("dependencies read stored root"); }
            @Override protected RuleDefinition loadDefinition(Long id) { return definition; }
            @Override protected RuleFunction loadFunction(Long id) { return null; }
        };
        ReflectionTestUtils.setField(preflight, "dependencyClosureService", dependencies);
        RuleDesignerService service = new RuleDesignerService();
        ReflectionTestUtils.setField(service, "preflightService", preflight);
        ReflectionTestUtils.setField(service, "lifecycleService", new RuleLifecycleService() {
            @Override public RuleRevision prepareDesignerDraft(Long id, RuleDraftSourceType sourceType, Long sourceId) {
                RuleRevision revision = new RuleRevision();
                revision.setId(8L);
                revision.setDefinitionId(id);
                revision.setModelJson("{\"script\":\"return 0;\"}");
                return revision;
            }
        });
        RuleDesignerCompileRequest request = new RuleDesignerCompileRequest();
        request.setModelJson("{\"script\":\"return 42;\",\"operand\":{\"kind\":\"FUNCTION\",\"functionId\":99,\"args\":[]}}");
        RuleDesignerCompileResponse result = service.compile(30L, request);
        assertTrue(result.isCompileSuccess());
        assertEquals("return 42;", result.getCompiledScript());
        assertFalse(result.getPreflightReport().isValid());
        assertTrue(result.getPreflightReport().getErrors().stream().anyMatch(issue ->
                "DEPENDENCY_NOT_FOUND".equals(issue.getCode()) && Long.valueOf(99L).equals(issue.getResourceId())));
        request.setModelJson("{\"script\":\"return 43;\"}");
        result = service.compile(30L, request);
        assertEquals("return 43;", result.getCompiledScript());
        assertTrue(result.getPreflightReport().getErrors().toString(), result.getPreflightReport().isValid());
        request.setModelJson("{\"script\":\"return 44;\",\"operand\":{\"kind\":\"RULE_CALL\",\"ruleId\":30}}");
        result = service.compile(30L, request);
        assertTrue(result.isCompileSuccess());
        assertFalse(result.getPreflightReport().isValid());
        assertTrue(result.getPreflightReport().getErrors().stream()
                .anyMatch(issue -> "RULE_DEPENDENCY_CYCLE".equals(issue.getCode())));
    }
}
