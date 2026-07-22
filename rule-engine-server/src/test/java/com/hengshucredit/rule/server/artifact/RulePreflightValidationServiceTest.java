package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.service.RuleReferenceIntegrityService;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class RulePreflightValidationServiceTest {

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
        service.inputs = Collections.singletonList(requiredInput);
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

    private static final class FixtureService extends RulePreflightValidationService {
        private final RuleRevision revision = new RuleRevision();
        private final RuleDefinition definition = new RuleDefinition();
        private RuleRevision previous;
        private RuleReferenceIntegrityService.AuditReport audit =
                new RuleReferenceIntegrityService.AuditReport(100L, Collections.emptyList());
        private CompileResult compileResult = CompileResult.ok("return true;", "QLEXPRESS");
        private RuleDependencyClosureService.DependencyClosure closure =
                RuleDependencyClosureService.DependencyClosure.of(Collections.emptyList(), Collections.emptyList());
        private List<RuleDefinitionInputField> inputs = Collections.emptyList();

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
        protected RuleDependencyClosureService.DependencyClosure resolveDependencies(
                RuleDefinition currentDefinition, RuleRevision currentRevision) {
            return closure;
        }

        @Override
        protected List<RuleDefinitionInputField> loadInputFields(Long definitionId) {
            return inputs;
        }

        @Override
        protected List<RuleDefinitionOutputField> loadOutputFields(Long definitionId) {
            return Collections.emptyList();
        }
    }
}
