package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.service.RuleReferenceIntegrityService;
import com.hengshucredit.rule.server.service.RuleFieldAnalyzer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RulePreflightValidationServiceTest {

    @Test
    public void reportIdentifiesTheExactDraftLockVersion() {
        FixtureService service = new FixtureService();
        service.revision.setLockVersion(7);
        Assert.assertEquals(Integer.valueOf(7), service.validate(200L).getLockVersion());
    }

    @Test
    public void aggregatesReferenceCompileAndDependencyErrors() {
        FixtureService service = new FixtureService();
        service.audit = new RuleReferenceIntegrityService.AuditReport(100L,
                Collections.singletonList(new RuleReferenceIntegrityService.ReferenceIssue(
                        100L, "$.input", "displayOnly", null, "VARIABLE",
                        "refId", "refType", "MISSING_CONTRACT", "缺少 ID")));
        service.compileResult = CompileResult.fail("编译失败");
        service.closure = RuleDependencyClosureService.DependencyClosure.of(
                Collections.emptyList(), Collections.singletonList(
                        RuleValidationIssue.error("INACTIVE_DEPENDENCY", "$", "依赖已停用")));

        RulePreflightReport report = service.validate(200L);

        Assert.assertFalse(report.isValid());
        Assert.assertTrue(report.getErrors().stream()
                .anyMatch(issue -> "REFERENCE_MISSING_CONTRACT".equals(issue.getCode())));
        Assert.assertTrue(report.getErrors().stream()
                .anyMatch(issue -> "COMPILE_FAILED".equals(issue.getCode())));
        Assert.assertTrue(report.getErrors().stream()
                .anyMatch(issue -> "INACTIVE_DEPENDENCY".equals(issue.getCode())));
    }

    @Test
    public void breakingSchemaIsAuditableButDoesNotBecomeHardError() {
        FixtureService service = new FixtureService();
        RuleDefinitionInputField requiredInput = new RuleDefinitionInputField();
        requiredInput.setFieldName("Customer_ID");
        requiredInput.setFieldType("STRING");
        requiredInput.setStatus(1);
        service.resolved = new RuleFieldAnalyzer.ResolvedFields(
                Collections.singletonList(requiredInput), Collections.emptyList());
        service.previous = new RuleRevision();
        service.previous.setInputSchemaJson("{\"type\":\"object\",\"properties\":{},"
                + "\"required\":[],\"additionalProperties\":false}");
        service.previous.setOutputSchemaJson("{\"type\":\"object\",\"properties\":{},"
                + "\"required\":[],\"additionalProperties\":false}");

        RulePreflightReport report = service.validate(200L);

        Assert.assertTrue(report.isValid());
        Assert.assertTrue(report.isBreakingSchemaChange());
        Assert.assertTrue(report.isBreakingChangeReasonRequired());
        Assert.assertEquals(64, report.getContentDigest().length());
        Assert.assertTrue(report.getWarnings().stream()
                .anyMatch(issue -> "BREAKING_SCHEMA_CHANGE".equals(issue.getCode())));
    }

    @Test
    public void preflightUsesFieldsResolvedFromRevisionSnapshotOnly() {
        FixtureService service = new FixtureService();
        service.resolved = new RuleFieldAnalyzer.ResolvedFields(
                Collections.singletonList(input("revision_input", "STRING", 7L, "VARIABLE")),
                Collections.singletonList(localOutput("credit_score_v1")));

        RulePreflightReport report = service.validate(200L);

        Assert.assertTrue(report.isValid());
        Assert.assertTrue(report.getInputSchemaJson().contains("revision_input"));
        Assert.assertFalse(report.getInputSchemaJson().contains("stale_projection"));
        Assert.assertEquals(0, service.persistedFieldLoadCount);
    }

    @Test
    public void stringSchemaOverrideUsesRuleSchemaTypeMapping() {
        FixtureService service = new FixtureService();
        service.resolved = resolvedWithInputOverride("NUMBER");

        RulePreflightReport report = service.validate(200L);

        Assert.assertTrue(report.getErrors().toString(), report.isValid());
        Assert.assertTrue(report.getInputSchemaJson().contains("\"type\":\"number\""));
        Assert.assertTrue(report.getInputSchemaJson().contains("\"x-rule-type\":\"NUMBER\""));
    }

    @Test
    public void invalidSchemaOverrideProducesDeterministicValidationError() {
        FixtureService service = new FixtureService();
        service.resolved = resolvedWithInputOverride(new Object());

        RulePreflightReport first = service.validate(200L);
        RulePreflightReport second = service.validate(200L);

        Assert.assertFalse(first.isValid());
        Assert.assertTrue(first.getErrors().stream()
                .anyMatch(issue -> "SCHEMA_INVALID".equals(issue.getCode())));
        Assert.assertEquals(first.getInputSchemaJson(), second.getInputSchemaJson());
        Assert.assertEquals(first.getOutputSchemaJson(), second.getOutputSchemaJson());
        Assert.assertEquals(first.getContentDigest(), second.getContentDigest());
    }

    private static RuleFieldAnalyzer.ResolvedFields resolvedWithInputOverride(Object override) {
        return new RuleFieldAnalyzer.ResolvedFields(
                Collections.singletonList(input("revision_input", "OBJECT", 7L, "VARIABLE")),
                Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                Collections.singletonMap("revision_input", override), Collections.emptyMap());
    }

    private static RuleDefinitionInputField input(
            String name, String type, Long refId, String refType) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setFieldName(name);
        field.setScriptName(name);
        field.setFieldType(type);
        field.setVarId(refId);
        field.setRefType(refType);
        field.setStatus(1);
        return field;
    }

    private static RuleDefinitionOutputField localOutput(String name) {
        RuleDefinitionOutputField field = new RuleDefinitionOutputField();
        field.setFieldName(name);
        field.setScriptName(name);
        field.setFieldType("NUMBER");
        field.setStatus(1);
        return field;
    }

    private static final class FixtureService extends RulePreflightValidationService {
        private final RuleRevision revision = new RuleRevision();
        private final RuleDefinition definition = new RuleDefinition();
        private RuleRevision previous;
        private RuleReferenceIntegrityService.AuditReport audit =
                new RuleReferenceIntegrityService.AuditReport(100L, Collections.emptyList());
        private CompileResult compileResult = CompileResult.ok("return true;", "QLEXPRESS");
        private RuleDependencyClosureService.DependencyClosure closure =
                RuleDependencyClosureService.DependencyClosure.of(Collections.emptyList(), Collections.emptyList());
        private RuleFieldAnalyzer.ResolvedFields resolved =
                new RuleFieldAnalyzer.ResolvedFields(Collections.emptyList(), Collections.emptyList());
        private int persistedFieldLoadCount;

        private FixtureService() {
            revision.setId(200L);
            revision.setDefinitionId(100L);
            revision.setState("REVIEW");
            revision.setModelJson("{}");
            definition.setId(100L);
            definition.setProjectId(9L);
            definition.setModelType("TABLE");
        }

        @Override
        protected RuleRevision loadRevision(Long revisionId) {
            return revision;
        }

        @Override
        protected RuleDefinition loadDefinition(Long definitionId) {
            return definition;
        }

        @Override
        protected RuleRevision loadPreviousPublishedRevision(RuleRevision current) {
            return previous;
        }

        @Override
        protected RuleReferenceIntegrityService.AuditReport auditReferences(
                RuleDefinition currentDefinition, RuleRevision currentRevision) {
            return audit;
        }

        @Override
        protected CompileResult compile(RuleDefinition currentDefinition, RuleRevision currentRevision) {
            return compileResult;
        }

        @Override
        protected RuleFieldAnalyzer.ResolvedFields resolveFields(
                RuleDefinition currentDefinition, RuleRevision currentRevision) {
            return resolved;
        }

        @Override
        protected RuleDependencyClosureService.DependencyClosure resolveDependencies(
                RuleDefinition currentDefinition, RuleRevision currentRevision,
                RuleFieldAnalyzer.ResolvedFields currentFields) {
            return closure;
        }

        @Override
        protected List<RuleDefinitionInputField> loadInputFields(Long definitionId) {
            persistedFieldLoadCount++;
            return Collections.singletonList(input(
                    "stale_projection", "STRING", 99L, "VARIABLE"));
        }

        @Override
        protected List<RuleDefinitionOutputField> loadOutputFields(Long definitionId) {
            persistedFieldLoadCount++;
            return Collections.singletonList(localOutput("stale_projection"));
        }
    }
}
