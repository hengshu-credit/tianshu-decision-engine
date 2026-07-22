package com.hengshucredit.rule.server.artifact;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import com.hengshucredit.rule.server.service.RuleCompileService;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleReferenceIntegrityService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RulePreflightValidationService {
    @Resource
    private RuleRevisionMapper revisionMapper;
    @Resource
    private RulePublishedMapper publishedMapper;
    @Resource
    private RuleDefinitionService definitionService;
    @Resource
    private RuleReferenceIntegrityService referenceIntegrityService;
    @Resource
    private RuleCompileService compileService;
    @Resource
    private RuleDependencyClosureService dependencyClosureService;

    private final RuleSchemaService schemaService = new RuleSchemaService();
    private final RuleSchemaCompatibilityService compatibilityService =
            new RuleSchemaCompatibilityService();

    public RulePreflightReport validate(Long revisionId) {
        RulePreflightReport report = new RulePreflightReport();
        report.setRevisionId(revisionId);
        RuleRevision revision = loadRevision(revisionId);
        if (revision == null) {
            report.getErrors().add(RuleValidationIssue.error("REVISION_NOT_FOUND", "$", "规则修订不存在"));
            report.setValid(false);
            return report;
        }
        RuleDefinition definition = loadDefinition(revision.getDefinitionId());
        if (definition == null) {
            report.getErrors().add(RuleValidationIssue.error("DEFINITION_NOT_FOUND", "$", "规则定义不存在"));
            report.setValid(false);
            return report;
        }

        RuleReferenceIntegrityService.AuditReport audit = auditReferences(definition, revision);
        for (RuleReferenceIntegrityService.ReferenceIssue issue : audit.getIssues()) {
            report.getErrors().add(RuleValidationIssue.error("REFERENCE_" + issue.getReason(),
                    issue.getPath(), issue.getRefType(), issue.getRefId(), issue.getMessage()));
        }

        CompileResult compileResult = compile(definition, revision);
        if (!compileResult.isSuccess()) {
            report.getErrors().add(RuleValidationIssue.error("COMPILE_FAILED", "$",
                    compileResult.getErrorMessage()));
        } else {
            report.setCompiledScript(compileResult.getCompiledScript());
            report.setCompiledType(compileResult.getCompiledType());
        }

        RuleDependencyClosureService.DependencyClosure closure = resolveDependencies(definition, revision);
        report.setDependencyDigest(closure.getDependencyDigest());
        for (RuleValidationIssue issue : closure.getIssues()) {
            addIssue(report, issue);
        }

        RuleSchemaService.SchemaSnapshot schemas;
        try {
            schemas = schemaService.build(loadInputFields(definition.getId()),
                    loadOutputFields(definition.getId()));
            report.setInputSchemaJson(schemas.getInputSchemaJson());
            report.setOutputSchemaJson(schemas.getOutputSchemaJson());
        } catch (IllegalArgumentException e) {
            report.getErrors().add(RuleValidationIssue.error("SCHEMA_INVALID", "$", e.getMessage()));
            schemas = schemaService.build(Collections.emptyList(), Collections.emptyList());
            report.setInputSchemaJson(schemas.getInputSchemaJson());
            report.setOutputSchemaJson(schemas.getOutputSchemaJson());
        }

        RuleRevision previous = loadPreviousPublishedRevision(revision);
        RuleSchemaCompatibilityService.CompatibilityReport compatibility = null;
        if (previous != null && !blank(previous.getInputSchemaJson())
                && !blank(previous.getOutputSchemaJson())) {
            try {
                compatibility = compatibilityService.compare(previous.getInputSchemaJson(),
                        previous.getOutputSchemaJson(), report.getInputSchemaJson(),
                        report.getOutputSchemaJson());
                report.setSchemaCompatibilityJson(CanonicalJson.write(compatibility));
                report.setBreakingSchemaChange(compatibility.hasBreakingChanges());
                if (compatibility.hasBreakingChanges()) {
                    report.getWarnings().add(RuleValidationIssue.warning("BREAKING_SCHEMA_CHANGE", "$",
                            "Schema 存在破坏性变更，批准时必须填写原因"));
                    report.setBreakingChangeReasonRequired(blank(revision.getForcePublishReason()));
                }
            } catch (IllegalArgumentException e) {
                report.getErrors().add(RuleValidationIssue.error("SCHEMA_COMPARISON_FAILED", "$", e.getMessage()));
            }
        }
        if (compatibility == null) {
            report.setSchemaCompatibilityJson(CanonicalJson.write(Map.of("changes", Collections.emptyList())));
        }

        Map<String, Object> digestSource = new LinkedHashMap<>();
        digestSource.put("definitionId", definition.getId());
        digestSource.put("revisionId", revision.getId());
        digestSource.put("modelJson", revision.getModelJson());
        digestSource.put("compiledScript", report.getCompiledScript());
        digestSource.put("compiledType", report.getCompiledType());
        digestSource.put("inputSchema", schemas.getInputSchema());
        digestSource.put("outputSchema", schemas.getOutputSchema());
        digestSource.put("dependencyDigest", closure.getDependencyDigest());
        report.setContentDigest(Sha256Digests.bytes(CanonicalJson.writeBytes(digestSource)));
        report.setValid(report.getErrors().isEmpty());
        return report;
    }

    private void addIssue(RulePreflightReport report, RuleValidationIssue issue) {
        if ("WARNING".equals(issue.getSeverity())) {
            report.getWarnings().add(issue);
        } else {
            report.getErrors().add(issue);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    protected RuleRevision loadRevision(Long revisionId) {
        return revisionMapper.selectById(revisionId);
    }

    protected RuleDefinition loadDefinition(Long definitionId) {
        return definitionService.getById(definitionId);
    }

    protected RuleRevision loadPreviousPublishedRevision(RuleRevision current) {
        if (current.getBaseRevisionId() != null) {
            RuleRevision base = revisionMapper.selectById(current.getBaseRevisionId());
            if (base != null && ("PUBLISHED".equals(base.getState()) || "OFFLINE".equals(base.getState()))) {
                return base;
            }
        }
        RulePublished published = publishedMapper.selectOne(new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, current.getDefinitionId())
                .last("LIMIT 1"));
        return published == null || published.getRevisionId() == null
                ? null : revisionMapper.selectById(published.getRevisionId());
    }

    protected RuleReferenceIntegrityService.AuditReport auditReferences(
            RuleDefinition definition, RuleRevision revision) {
        return referenceIntegrityService.audit(definition.getId(), definition.getProjectId(),
                revision.getModelJson());
    }

    protected CompileResult compile(RuleDefinition definition, RuleRevision revision) {
        return compileService.compilePreview(definition.getId(), revision.getModelJson(), definition.getModelType());
    }

    protected RuleDependencyClosureService.DependencyClosure resolveDependencies(
            RuleDefinition definition, RuleRevision revision) {
        return dependencyClosureService.resolve(definition.getId(), revision.getId());
    }

    protected List<RuleDefinitionInputField> loadInputFields(Long definitionId) {
        return definitionService.listInputFields(definitionId);
    }

    protected List<RuleDefinitionOutputField> loadOutputFields(Long definitionId) {
        return definitionService.listOutputFields(definitionId);
    }
}
