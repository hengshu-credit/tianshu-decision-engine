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
import com.hengshucredit.rule.server.service.RuleFieldAnalyzer;
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
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RulePreflightValidationService.class);
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
    @Resource
    private RuleFieldAnalyzer ruleFieldAnalyzer;
    @Resource
    private com.hengshucredit.rule.server.service.RuleRegressionEvidenceService regressionEvidence;

    private final RuleSchemaService schemaService = new RuleSchemaService();
    private final RuleSchemaCompatibilityService compatibilityService =
            new RuleSchemaCompatibilityService();

    public RulePreflightReport validate(Long revisionId) {
        RuleRevision revision = loadRevision(revisionId);
        RulePreflightReport report = validatePreview(revision);
        report.setRevisionId(revisionId);
        if (report.isValid()) regressionEvidence.appendGate(revision.getDefinitionId(), report);
        return report;
    }

    /** 与持久化预检共享校验，但绝不插入临时修订。 */
    public RulePreflightReport validatePreview(RuleRevision revision) {
        return validatePreview(revision, true);
    }

    public RulePreflightReport validatePreview(RuleRevision revision, boolean includeRegressionSummary) {
        return analyze(revision, includeRegressionSummary).report();
    }

    /** Analysis stays within this operation; never reuse it as a later publication authorization. */
    public record Analysis(RulePreflightReport report, RuleFieldAnalyzer.ResolvedFields fields,
                           RuleDependencyClosureService.DependencyClosure dependencies, String modelDigest) { }

    public Analysis analyzeForPublication(Long revisionId) {
        RuleRevision revision = loadRevision(revisionId);
        Analysis analysis = analyze(revision, true);
        if (analysis.report().isValid()) regressionEvidence.appendGate(revision.getDefinitionId(), analysis.report());
        return analysis;
    }

    public Analysis analyze(RuleRevision revision, boolean includeRegressionSummary) {
        long started = System.nanoTime();
        Map<String, Long> durations = new LinkedHashMap<>();
        RulePreflightReport report = new RulePreflightReport();
        report.setRevisionId(revision == null ? null : revision.getId());
        if (revision == null) {
            report.getErrors().add(RuleValidationIssue.error("REVISION_NOT_FOUND", "$", "规则修订不存在"));
            report.setValid(false);
            return new Analysis(report, null, null, null);
        }
        RuleDefinition definition = loadDefinition(revision.getDefinitionId());
        report.setLockVersion(revision.getLockVersion());
        if (definition == null) {
            report.getErrors().add(RuleValidationIssue.error("DEFINITION_NOT_FOUND", "$", "规则定义不存在"));
            report.setValid(false);
            return new Analysis(report, null, null, null);
        }

        long stage = System.nanoTime();
        RuleFieldAnalyzer.ResolvedFields fields = resolveFields(definition, revision);
        stage = recordDuration(durations, "fields", stage);
        fields.getDiagnostics().forEach(issue -> issue.setRevisionId(revision.getId()));
        fields.getDiagnostics().forEach(issue -> addIssue(report, issue));

        RuleReferenceIntegrityService.AuditReport audit = auditReferences(definition, revision);
        stage = recordDuration(durations, "references", stage);
        for (RuleReferenceIntegrityService.ReferenceIssue issue : audit.getIssues()) {
            report.getErrors().add(RuleValidationIssue.error("REFERENCE_" + issue.getReason(),
                    issue.getPath(), issue.getRefType(), issue.getRefId(), issue.getMessage()));
        }

        CompileResult compileResult = compile(definition, revision);
        stage = recordDuration(durations, "compile", stage);
        if (!compileResult.isSuccess()) {
            report.getErrors().add(RuleValidationIssue.error("COMPILE_FAILED", "$",
                    compileResult.getErrorMessage()));
        } else {
            report.setCompiledScript(compileResult.getCompiledScript());
            report.setCompiledType(compileResult.getCompiledType());
        }

        RuleRevision compiledRevision = new RuleRevision();
        org.springframework.beans.BeanUtils.copyProperties(revision, compiledRevision);
        compiledRevision.setCompiledScript(report.getCompiledScript());
        RuleDependencyClosureService.DependencyClosure closure =
                resolveDependencies(definition, compiledRevision, fields);
        stage = recordDuration(durations, "dependencies", stage);
        report.setDependencyDigest(closure.getDependencyDigest());
        for (RuleValidationIssue issue : closure.getIssues()) {
            addIssue(report, issue);
        }

        RuleSchemaService.SchemaSnapshot schemas;
        try {
            schemas = schemaService.build(fields.getInputFields(), fields.getOutputFields(),
                    propertySchemas(fields.getInputPropertySchemas()),
                    propertySchemas(fields.getOutputPropertySchemas()));
            report.setInputSchemaJson(schemas.getInputSchemaJson());
            report.setOutputSchemaJson(schemas.getOutputSchemaJson());
            report.setFieldDigest(fieldDigest(fields));
        } catch (IllegalArgumentException e) {
            report.getErrors().add(RuleValidationIssue.error("SCHEMA_INVALID", "$", e.getMessage()));
            schemas = schemaService.build(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyMap(), Collections.emptyMap());
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
        stage = recordDuration(durations, "schemasAndDigests", stage);
        if (includeRegressionSummary && report.isValid()) regressionEvidence.appendSummary(definition.getId(), report);
        recordDuration(durations, "regression", stage);
        recordDuration(durations, "total", started);
        LOG.info("Rule analysis definitionId={} revisionId={} valid={} durationMs={}",
                revision.getDefinitionId(), revision.getId(), report.isValid(), durations);
        return new Analysis(report, fields, closure, Sha256Digests.text(revision.getModelJson()));
    }

    private long recordDuration(Map<String, Long> durations, String stage, long started) {
        long now = System.nanoTime();
        durations.put(stage, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(now - started));
        return now;
    }

    private void addIssue(RulePreflightReport report, RuleValidationIssue issue) {
        if ("WARNING".equals(issue.getSeverity())) {
            report.getWarnings().add(issue);
        } else {
            report.getErrors().add(issue);
        }
    }

    public static String fieldDigest(RuleFieldAnalyzer.ResolvedFields fields) {
        return Sha256Digests.text(CanonicalJson.write(Map.of("inputs", fieldContracts(fields.getInputFields()),
                "outputs", fieldContracts(fields.getOutputFields()), "inputProperties", fields.getInputPropertySchemas(),
                "outputProperties", fields.getOutputPropertySchemas())));
    }

    private static List<Map<String, Object>> fieldContracts(List<?> fields) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object field : fields) {
            var bean = new org.springframework.beans.BeanWrapperImpl(field);
            Map<String, Object> contract = new LinkedHashMap<>();
            for (var property : bean.getPropertyDescriptors()) {
                String name = property.getName();
                if (List.of("class", "id", "definitionId", "createTime", "updateTime").contains(name)) continue;
                contract.put(name, bean.getPropertyValue(name));
            }
            result.add(contract);
        }
        return result;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Map<String, Map<String, Object>> propertySchemas(Map<String, Object> schemas) {
        if (schemas == null || schemas.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schemas.entrySet()) {
            String fieldName = entry.getKey();
            if (fieldName == null || fieldName.isBlank()) {
                throw new IllegalArgumentException("Schema 覆盖字段名不能为空");
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                result.put(fieldName, deepCopySchemaMap(map));
            } else if (value instanceof String type) {
                result.put(fieldName, schemaForRuleType(type));
            } else {
                throw new IllegalArgumentException("Schema 覆盖必须是对象或已知字段类型: " + fieldName);
            }
        }
        return result;
    }

    private Map<String, Object> schemaForRuleType(String ruleType) {
        if (ruleType == null || ruleType.isBlank()) {
            throw new IllegalArgumentException("Schema 字段类型不能为空");
        }
        String normalized = ruleType.toUpperCase(java.util.Locale.ROOT);
        Map<String, Object> schema = new LinkedHashMap<>();
        String jsonType = switch (normalized) {
            case "INTEGER", "LONG", "SHORT", "BYTE" -> "integer";
            case "NUMBER", "DOUBLE", "FLOAT", "DECIMAL", "BIGDECIMAL" -> "number";
            case "BOOLEAN", "BOOL" -> "boolean";
            case "ARRAY", "LIST", "SET" -> "array";
            case "OBJECT", "MAP" -> "object";
            case "STRING", "DATE", "DATETIME", "LOCALDATETIME" -> "string";
            default -> throw new IllegalArgumentException("未知的 Schema 字段类型: " + ruleType);
        };
        schema.put("type", jsonType);
        if ("DATE".equals(normalized)) {
            schema.put("format", "date");
        } else if ("DATETIME".equals(normalized) || "LOCALDATETIME".equals(normalized)) {
            schema.put("format", "date-time");
        }
        schema.put("x-rule-type", ruleType);
        return schema;
    }

    private Map<String, Object> deepCopySchemaMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Schema 覆盖对象键必须是字符串");
            }
            copy.put(key, deepCopySchemaValue(entry.getValue()));
        }
        return copy;
    }

    private Object deepCopySchemaValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCopySchemaMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepCopySchemaValue(item));
            }
            return copy;
        }
        return value;
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
        return compileService.compileSyntaxPreview(definition.getId(), revision.getModelJson(), definition.getModelType());
    }

    protected RuleFieldAnalyzer.ResolvedFields resolveFields(
            RuleDefinition definition, RuleRevision revision) {
        return ruleFieldAnalyzer.resolveFields(null, revision.getModelJson(),
                definition.getModelType(), definition.getProjectId());
    }

    protected RuleDependencyClosureService.DependencyClosure resolveDependencies(
            RuleDefinition definition, RuleRevision revision,
            RuleFieldAnalyzer.ResolvedFields fields) {
        return dependencyClosureService.resolvePreview(revision, fields);
    }

    protected List<RuleDefinitionInputField> loadInputFields(Long definitionId) {
        return definitionService.listInputFields(definitionId);
    }

    protected List<RuleDefinitionOutputField> loadOutputFields(Long definitionId) {
        return definitionService.listOutputFields(definitionId);
    }
}
