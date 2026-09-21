package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.enums.RuleRevisionState;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.RuleDependencyClosureService;
import com.hengshucredit.rule.server.artifact.RuleSchemaService;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import com.hengshucredit.rule.server.openapi.OpenApiContractCodec;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RuleDraftService {
    private static final Set<String> ALLOWED_COMPILE_ISSUE_CODES =
            Set.of("QL_PARSE_ERROR", "MODEL_JSON_INVALID",
                    "SCRIPT_EMPTY", "UNSUPPORTED_MODEL_TYPE",
                    "RULE_CALL_CYCLE", "COMPILE_FAILED");
    @Resource
    private RuleDefinitionMapper definitionMapper;
    @Resource
    private RuleRevisionMapper revisionMapper;
    @Resource
    private RuleDefinitionContentMapper contentMapper;
    @Resource
    @Lazy
    private RuleLifecycleService lifecycleService;
    @Resource
    private RuleFieldAnalyzer fieldAnalyzer;
    @Resource
    @Lazy
    private RuleCompileService compileService;
    @Resource
    @Lazy
    private RuleReferenceIntegrityService referenceIntegrityService;
    @Resource
    private RuleCallCycleService ruleCallCycleService;
    @Resource
    @Lazy
    private RuleDependencyClosureService dependencyClosureService;
    @Resource
    private ConsoleOperatorResolver operatorResolver;

    private final RuleSchemaService schemaService = new RuleSchemaService();

    @Transactional
    public RuleDraftSaveResponse save(RuleDraftSaveRequest request) {
        requireSaveContract(request);
        RuleDefinition definition = loadDefinition(request.getDefinitionId());
        if (definition == null) {
            throw governance(400, "DEFINITION_NOT_FOUND", "规则定义不存在");
        }
        RuleRevision revision = requireEditableDraft(
                request.getDefinitionId(), request.getRevisionId());
        if (revision == null
                || !request.getDefinitionId().equals(revision.getDefinitionId())) {
            throw governance(400, "REVISION_DEFINITION_MISMATCH",
                    "修订不属于指定规则");
        }
        if (!RuleRevisionState.DRAFT.name().equals(revision.getState())) {
            throw governance(409, "FROZEN_REVISION_WRITE_REJECTED",
                    "只能保存 DRAFT 状态的规则修订");
        }

        List<RuleValidationIssue> issues = new ArrayList<>();
        addReferenceDiagnostics(definition, request.getModelJson(),
                request.getRevisionId(), issues);
        addCycleDiagnostic(definition, request.getModelJson(),
                request.getRevisionId(), issues);

        RuleFieldAnalyzer.ResolvedFields fields =
                resolveFields(definition, request.getModelJson());
        for (RuleValidationIssue issue : fields.getDiagnostics()) {
            if (issue.getRevisionId() == null) {
                issue.setRevisionId(request.getRevisionId());
            }
            issues.add(issue);
        }

        CompileResult compileResult =
                compile(definition, request.getModelJson());
        if (!compileResult.isSuccess()) {
            issues.add(compileIssue(compileResult.getErrorMessage(),
                    request.getRevisionId()));
        }

        revision.setModelJson(request.getModelJson());
        RuleDependencyClosureService.DependencyClosure dependencies =
                resolveDependencies(definition, revision, fields);
        for (RuleValidationIssue issue : dependencies.getIssues()) {
            if (issue.getRevisionId() == null) {
                issue.setRevisionId(request.getRevisionId());
            }
            issues.add(issue);
        }

        RuleSchemaService.SchemaSnapshot schemas;
        try {
            schemas = schemaService.build(
                    fields.getInputFields(), fields.getOutputFields(),
                    propertySchemas(fields.getInputPropertySchemas()),
                    propertySchemas(fields.getOutputPropertySchemas()));
        } catch (IllegalArgumentException e) {
            issues.add(RuleValidationIssue.error(
                    "SCHEMA_INVALID", "$", e.getMessage())
                    .withRevisionId(request.getRevisionId()));
            schemas = schemaService.build(
                    Collections.emptyList(), Collections.emptyList());
        }

        boolean compileSuccess = compileResult.isSuccess();
        String compileMessage = compileResult.isSuccess()
                ? firstErrorMessage(issues) : compileResult.getErrorMessage();
        String openApiConfigJson = revision.getOpenApiConfigJson();
        if (Boolean.TRUE.equals(request.getUpdateOpenApiConfig())) {
            openApiConfigJson =
                    normalizeOpenApiConfig(request.getOpenApiConfigJson());
        }

        revision.setModelJson(request.getModelJson());
        revision.setCompiledScript(compileResult.getCompiledScript());
        revision.setCompiledType(compileResult.getCompiledType());
        revision.setOpenApiConfigJson(openApiConfigJson);
        revision.setInputSchemaJson(schemas.getInputSchemaJson());
        revision.setOutputSchemaJson(schemas.getOutputSchemaJson());
        revision.setContentDigest(contentDigest(
                definition, revision, dependencies.getDependencyDigest()));
        revision.setValidationReportDigest(
                Sha256Digests.bytes(CanonicalJson.writeBytes(issues)));
        revision.setLockVersion(request.getLockVersion() + 1);
        revision.setUpdateBy(actor());
        revision.setUpdateTime(LocalDateTime.now());

        if (!compareAndSetDraft(revision, request.getLockVersion())) {
            throw governance(409, "DRAFT_LOCK_CONFLICT",
                    "草稿已被其他会话修改，请刷新后重试");
        }

        RuleDefinitionContent content = loadContent(definition.getId());
        boolean insertContent = content == null;
        if (insertContent) {
            content = new RuleDefinitionContent();
            content.setDefinitionId(definition.getId());
        }
        content.setModelJson(request.getModelJson());
        content.setCompiledScript(compileResult.getCompiledScript());
        content.setCompiledType(compileResult.getCompiledType());
        content.setCompileStatus(compileSuccess ? 1 : 2);
        content.setCompileMessage(compileMessage);
        content.setCompileTime(LocalDateTime.now());
        content.setOpenApiConfigJson(openApiConfigJson);
        if ("SCRIPT".equalsIgnoreCase(definition.getModelType())) {
            content.setScriptMode("script");
        }
        persistContent(content);
        persistResolvedFields(definition.getId(), fields);
        int designVersion = incrementDesignVersion(definition);

        RuleDraftSaveResponse response = new RuleDraftSaveResponse();
        response.setRevision(revision);
        response.setDesignVersion(designVersion);
        response.setCompileSuccess(compileSuccess);
        response.setCompileMessage(compileMessage);
        response.setIssues(issues);
        return response;
    }

    /**
     * 审批驳回后将兼容业务投影恢复为当前生效修订。
     * 审批修订和事件仍然完整保留，仅覆盖详情页、设计器读取的当前内容投影。
     */
    @Transactional
    public void restoreEffectiveProjection(RuleRevision effective) {
        if (effective == null || effective.getDefinitionId() == null
                || effective.getModelJson() == null) {
            return;
        }
        RuleDefinition definition = loadDefinition(effective.getDefinitionId());
        if (definition == null) {
            throw governance(400, "DEFINITION_NOT_FOUND", "规则定义不存在");
        }
        RuleFieldAnalyzer.ResolvedFields fields =
                resolveFields(definition, effective.getModelJson());
        RuleDefinitionContent content = loadContent(definition.getId());
        if (content == null) {
            content = new RuleDefinitionContent();
            content.setDefinitionId(definition.getId());
        }
        content.setModelJson(effective.getModelJson());
        content.setCompiledScript(effective.getCompiledScript());
        content.setCompiledType(effective.getCompiledType());
        content.setCompileStatus(1);
        content.setCompileMessage(null);
        content.setCompileTime(LocalDateTime.now());
        content.setOpenApiConfigJson(effective.getOpenApiConfigJson());
        if ("SCRIPT".equalsIgnoreCase(definition.getModelType())) {
            content.setScriptMode("script");
        }
        persistContent(content);
        persistResolvedFields(definition.getId(), fields);
        incrementDesignVersion(definition);
    }

    /** No draft or formal source remains. Do not reopen the deleted draft through the legacy projection. */
    @Transactional
    public void clearDesignerProjection(Long definitionId) {
        contentMapper.update(null, new LambdaUpdateWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, definitionId)
                .set(RuleDefinitionContent::getModelJson, "{}")
                .set(RuleDefinitionContent::getCompiledScript, null)
                .set(RuleDefinitionContent::getCompiledType, null)
                .set(RuleDefinitionContent::getCompileStatus, 0)
                .set(RuleDefinitionContent::getCompileMessage, null)
                .set(RuleDefinitionContent::getCompileTime, null)
                .set(RuleDefinitionContent::getOpenApiConfigJson, null));
        persistResolvedFields(definitionId, new RuleFieldAnalyzer.ResolvedFields(Collections.emptyList(), Collections.emptyList()));
    }

    private void requireSaveContract(RuleDraftSaveRequest request) {
        if (request == null || request.getDefinitionId() == null
                || request.getRevisionId() == null
                || request.getLockVersion() == null
                || request.getModelJson() == null
                || request.getModelJson().trim().isEmpty()) {
            throw governance(400, "DRAFT_SAVE_CONTRACT_INVALID",
                    "definitionId、revisionId、lockVersion 和 modelJson 均为必填项");
        }
        if (request.getLockVersion() < 0) {
            throw governance(400, "DRAFT_SAVE_CONTRACT_INVALID",
                    "lockVersion 不能小于 0");
        }
    }

    private void addReferenceDiagnostics(
            RuleDefinition definition, String modelJson, Long revisionId,
            List<RuleValidationIssue> issues) {
        RuleReferenceIntegrityService.AuditReport audit =
                auditReferences(definition, modelJson);
        for (RuleReferenceIntegrityService.ReferenceIssue issue
                : audit.getIssues()) {
            String reason = issue.getReason() == null
                    ? "INVALID" : issue.getReason();
            issues.add(RuleValidationIssue.error(
                    "REFERENCE_" + reason, issue.getPath(),
                    issue.getRefType(), issue.getRefId(), issue.getMessage())
                    .withRevisionId(revisionId));
        }
    }

    private void addCycleDiagnostic(
            RuleDefinition definition, String modelJson, Long revisionId,
            List<RuleValidationIssue> issues) {
        String cycleError = validateCallCycle(definition, modelJson);
        if (cycleError != null) {
            issues.add(RuleValidationIssue.error(
                    "RULE_CALL_CYCLE", "$.ruleCalls", cycleError)
                    .withRevisionId(revisionId));
        }
    }

    private RuleValidationIssue compileIssue(
            String message, Long revisionId) {
        String code = "COMPILE_FAILED";
        if (message != null) {
            int delimiter = message.indexOf(':');
            String candidate = delimiter < 0
                    ? message.trim() : message.substring(0, delimiter).trim();
            if (ALLOWED_COMPILE_ISSUE_CODES.contains(candidate)) {
                code = candidate;
            }
        }
        return RuleValidationIssue.error(code, "$.script",
                message == null ? "规则编译失败" : message)
                .withRevisionId(revisionId);
    }

    private String firstErrorMessage(List<RuleValidationIssue> issues) {
        return issues.stream()
                .filter(issue -> "ERROR".equals(issue.getSeverity()))
                .map(RuleValidationIssue::getMessage)
                .findFirst().orElse(null);
    }

    private String contentDigest(
            RuleDefinition definition, RuleRevision revision,
            String dependencyDigest) {
        Map<String, Object> digestSource = new LinkedHashMap<>();
        digestSource.put("definitionId", definition.getId());
        digestSource.put("revisionId", revision.getId());
        digestSource.put("modelJson", revision.getModelJson());
        digestSource.put("compiledScript", revision.getCompiledScript());
        digestSource.put("compiledType", revision.getCompiledType());
        digestSource.put("inputSchemaJson", revision.getInputSchemaJson());
        digestSource.put("outputSchemaJson", revision.getOutputSchemaJson());
        digestSource.put("dependencyDigest", dependencyDigest);
        return Sha256Digests.bytes(CanonicalJson.writeBytes(digestSource));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> propertySchemas(
            Map<String, Object> schemas) {
        if (schemas == null || schemas.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schemas.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?>) {
                result.put(entry.getKey(),
                        (Map<String, Object>) entry.getValue());
            }
        }
        return result;
    }

    private RuleGovernanceException governance(
            int httpStatus, String code, String message) {
        return new RuleGovernanceException(
                httpStatus, code, message,
                Collections.singletonList(
                        RuleValidationIssue.error(code, "$", message)));
    }

    protected RuleDefinition loadDefinition(Long definitionId) {
        return definitionMapper.selectById(definitionId);
    }

    protected RuleRevision requireEditableDraft(
            Long definitionId, Long revisionId) {
        return lifecycleService.requireEditableDraft(
                definitionId, revisionId);
    }

    protected RuleFieldAnalyzer.ResolvedFields resolveFields(
            RuleDefinition definition, String modelJson) {
        return fieldAnalyzer.resolveFields(
                definition.getId(), modelJson,
                definition.getModelType(), definition.getProjectId());
    }

    protected RuleReferenceIntegrityService.AuditReport auditReferences(
            RuleDefinition definition, String modelJson) {
        if (referenceIntegrityService == null) {
            return new RuleReferenceIntegrityService.AuditReport(
                    definition.getId(), Collections.emptyList());
        }
        return referenceIntegrityService.audit(
                definition.getId(), definition.getProjectId(), modelJson);
    }

    protected String validateCallCycle(
            RuleDefinition definition, String modelJson) {
        return ruleCallCycleService == null ? null
                : ruleCallCycleService.validateNoCycle(
                definition.getId(), modelJson);
    }

    protected CompileResult compile(
            RuleDefinition definition, String modelJson) {
        return compileService.compileSyntaxPreview(
                definition.getId(), modelJson, definition.getModelType());
    }

    protected RuleDependencyClosureService.DependencyClosure
    resolveDependencies(
            RuleDefinition definition, RuleRevision revision,
            RuleFieldAnalyzer.ResolvedFields fields) {
        if (dependencyClosureService == null) {
            return RuleDependencyClosureService.DependencyClosure.of(
                    Collections.emptyList(), Collections.emptyList());
        }
        return dependencyClosureService.resolvePreview(revision, fields);
    }

    protected String normalizeOpenApiConfig(String configJson) {
        try {
            return OpenApiContractCodec.validateAndNormalize(configJson);
        } catch (IllegalArgumentException e) {
            throw governance(400, "OPEN_API_CONFIG_INVALID",
                    e.getMessage());
        }
    }

    protected boolean compareAndSetDraft(
            RuleRevision revision, int expectedLockVersion) {
        int updated = revisionMapper.update(
                null, new LambdaUpdateWrapper<RuleRevision>()
                        .eq(RuleRevision::getId, revision.getId())
                        .eq(RuleRevision::getState,
                                RuleRevisionState.DRAFT.name())
                        .eq(RuleRevision::getLockVersion,
                                expectedLockVersion)
                        .set(RuleRevision::getModelJson,
                                revision.getModelJson())
                        .set(RuleRevision::getCompiledScript,
                                revision.getCompiledScript())
                        .set(RuleRevision::getCompiledType,
                                revision.getCompiledType())
                        .set(RuleRevision::getOpenApiConfigJson,
                                revision.getOpenApiConfigJson())
                        .set(RuleRevision::getInputSchemaJson,
                                revision.getInputSchemaJson())
                        .set(RuleRevision::getOutputSchemaJson,
                                revision.getOutputSchemaJson())
                        .set(RuleRevision::getContentDigest,
                                revision.getContentDigest())
                        .set(RuleRevision::getValidationReportDigest,
                                revision.getValidationReportDigest())
                        .set(RuleRevision::getLockVersion,
                                revision.getLockVersion())
                        .set(RuleRevision::getUpdateBy,
                                revision.getUpdateBy())
                        .set(RuleRevision::getUpdateTime,
                                revision.getUpdateTime()));
        return updated == 1;
    }

    protected RuleDefinitionContent loadContent(Long definitionId) {
        return contentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query
                        .LambdaQueryWrapper<RuleDefinitionContent>()
                        .eq(RuleDefinitionContent::getDefinitionId,
                                definitionId));
    }

    protected void persistContent(RuleDefinitionContent content) {
        int written;
        if (content.getId() == null) {
            written = contentMapper.insert(content);
        } else {
            // MyBatis 的 NOT_NULL 更新策略不会清除旧脚本；失败草稿不能残留可执行投影。
            written = contentMapper.update(content,
                    new LambdaUpdateWrapper<RuleDefinitionContent>()
                            .eq(RuleDefinitionContent::getId, content.getId())
                            .set(content.getCompiledScript() == null,
                                    RuleDefinitionContent::getCompiledScript, null)
                            .set(content.getCompiledType() == null,
                                    RuleDefinitionContent::getCompiledType, null)
                            .set(content.getCompileMessage() == null,
                                    RuleDefinitionContent::getCompileMessage, null)
                            .set(content.getOpenApiConfigJson() == null,
                                    RuleDefinitionContent::getOpenApiConfigJson, null));
        }
        if (written != 1) {
            throw new IllegalStateException(
                    "规则内容投影写入失败");
        }
    }

    protected void persistResolvedFields(
            Long definitionId,
            RuleFieldAnalyzer.ResolvedFields fields) {
        fieldAnalyzer.persistResolvedFields(definitionId, fields);
    }

    protected int incrementDesignVersion(RuleDefinition definition) {
        int currentVersion = definition.getCurrentVersion() == null
                ? 0 : definition.getCurrentVersion();
        int nextVersion = currentVersion + 1;
        LambdaUpdateWrapper<RuleDefinition> update =
                new LambdaUpdateWrapper<RuleDefinition>()
                        .eq(RuleDefinition::getId,
                                definition.getId());
        if (definition.getCurrentVersion() == null) {
            update.isNull(RuleDefinition::getCurrentVersion);
        } else {
            update.eq(RuleDefinition::getCurrentVersion,
                    currentVersion);
        }
        int updated = definitionMapper.update(
                null, update
                        .set(RuleDefinition::getCurrentVersion, nextVersion)
                        .set(RuleDefinition::getUpdateBy, actor())
                        .set(RuleDefinition::getUpdateTime,
                                LocalDateTime.now()));
        if (updated != 1) {
            throw governance(409, "DRAFT_LOCK_CONFLICT",
                    "规则设计版本已被其他会话修改，请刷新后重试");
        }
        definition.setCurrentVersion(nextVersion);
        return nextVersion;
    }

    protected String actor() {
        return operatorResolver == null
                ? "system" : operatorResolver.resolve();
    }
}
