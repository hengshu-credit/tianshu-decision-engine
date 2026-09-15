package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.RuleDesignerCompileRequest;
import com.hengshucredit.rule.model.dto.RuleDesignerCompileResponse;
import com.hengshucredit.rule.model.dto.RuleDesignerDraftRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDesignerSaveOperation;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.RulePreflightValidationService;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.mapper.RuleDesignerSaveOperationMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RuleDesignerService {
    @Resource private RuleLifecycleService lifecycleService;
    @Resource private RuleDraftService draftService;
    @Resource private RulePreflightValidationService preflightService;
    @Resource private RuleDesignerSaveOperationMapper operationMapper;
    @Resource private ConsoleOperatorResolver operatorResolver;

    public RuleDesignerCompileResponse compile(Long definitionId, RuleDesignerCompileRequest request) {
        requirePayload(request);
        return compile(prepare(definitionId, request));
    }

    private RuleDesignerCompileResponse compile(RuleRevision revision) {
        RulePreflightReport report = preview(revision);
        boolean compiled = report.getCompiledScript() != null && report.getErrors().stream()
                .noneMatch(issue -> "COMPILE_FAILED".equals(issue.getCode()));
        RuleDesignerCompileResponse response = new RuleDesignerCompileResponse();
        response.setCompileSuccess(compiled);
        response.setCompileMessage(compiled ? null : report.getErrors().stream()
                .filter(issue -> "COMPILE_FAILED".equals(issue.getCode()))
                .map(RuleValidationIssue::getMessage).findFirst().orElse("规则编译失败"));
        response.setCompiledScript(report.getCompiledScript());
        response.setCompiledType(report.getCompiledType());
        response.setPreflightReport(report);
        return response;
    }

    @Transactional
    public RuleDraftSaveResponse save(Long definitionId, RuleDesignerDraftRequest request) {
        requirePayload(request);
        boolean overwrite = "OVERWRITE".equals(request.getSaveMode());
        if ((!overwrite && !"NEW".equals(request.getSaveMode()))
                || request.getRequestId() == null || request.getRequestId().isBlank()
                || request.getRequestId().length() > 128
                || (overwrite && (request.getRevisionId() == null || request.getLockVersion() == null
                || request.getLockVersion() < 0))
                || (!overwrite && (request.getRevisionId() != null || request.getLockVersion() != null))) {
            throw error(400, "DRAFT_SAVE_CONTRACT_INVALID", "请提供保存方式、请求标识，以及覆盖时的草稿 ID 和锁版本");
        }
        lockDefinition(definitionId);
        String digest = Sha256Digests.text(CanonicalJson.write(
                Map.of("request", request, "actor", actor())));
        RuleDesignerSaveOperation existing = findOperation(definitionId, request.getRequestId());
        if (existing != null) {
            if (!digest.equals(existing.getRequestDigest())) {
                throw error(409, "REQUEST_ID_REUSED", "请求标识已用于不同保存内容，请使用新的请求标识");
            }
            return JSON.parseObject(existing.getResponseJson(), RuleDraftSaveResponse.class);
        }

        RuleRevision revision;
        if (overwrite) {
            revision = editable(definitionId, request.getRevisionId());
            if (!request.getLockVersion().equals(revision.getLockVersion())) {
                throw error(409, "DRAFT_LOCK_CONFLICT", "草稿已变化，请核对当前内容后重试");
            }
            if (request.getSourceType() != null && (!"REVISION".equals(request.getSourceType().name())
                    || !revision.getId().equals(request.getSourceId()))) {
                throw error(400, "SOURCE_REVISION_MISMATCH", "覆盖来源必须是当前草稿");
            }
        } else {
            revision = prepare(definitionId, request);
        }
        revision.setModelJson(request.getModelJson());
        RuleDesignerCompileResponse compiled = compile(revision);
        if (!compiled.isCompileSuccess()) {
            throw new RuleGovernanceException(400, "COMPILE_FAILED", compiled.getCompileMessage(),
                    compiled.getPreflightReport().getErrors());
        }
        if (!overwrite) insertDraft(revision);

        RuleDraftSaveRequest save = new RuleDraftSaveRequest();
        save.setDefinitionId(definitionId);
        save.setRevisionId(revision.getId());
        save.setLockVersion(revision.getLockVersion());
        save.setModelJson(request.getModelJson());
        save.setOpenApiConfigJson(request.getOpenApiConfigJson());
        save.setUpdateOpenApiConfig(request.getUpdateOpenApiConfig());
        RuleDraftSaveResponse response = saveDraft(save);
        List<RuleValidationIssue> issues = new ArrayList<>(compiled.getPreflightReport().getErrors());
        issues.addAll(compiled.getPreflightReport().getWarnings());
        for (RuleValidationIssue issue : response.getIssues()) {
            if (issues.stream().noneMatch(prior -> prior.getCode().equals(issue.getCode())
                    && java.util.Objects.equals(prior.getPath(), issue.getPath()))) issues.add(issue);
        }
        response.setIssues(issues);
        recordSave(response.getRevision(), request.getSaveMode());
        RuleDesignerSaveOperation operation = new RuleDesignerSaveOperation();
        operation.setDefinitionId(definitionId);
        operation.setRequestId(request.getRequestId());
        operation.setRequestDigest(digest);
        operation.setResponseJson(JSON.toJSONString(response));
        operation.setCreateBy(actor());
        operation.setCreateTime(LocalDateTime.now());
        insertOperation(operation);
        return response;
    }

    private void requirePayload(RuleDesignerCompileRequest request) {
        if (request == null || request.getModelJson() == null || request.getModelJson().isBlank()
                || ((request.getSourceType() == null) != (request.getSourceId() == null))) {
            throw error(400, "DESIGNER_CONTRACT_INVALID", "请提供当前配置，来源类型与来源 ID 必须同时提供");
        }
    }

    private RuleGovernanceException error(int status, String code, String message) {
        return new RuleGovernanceException(status, code, message,
                List.of(RuleValidationIssue.error(code, "$", message)));
    }

    protected void lockDefinition(Long definitionId) {
        if (lifecycleService.lockDefinition(definitionId) == null)
            throw error(400, "DEFINITION_NOT_FOUND", "规则定义不存在");
    }
    protected RuleRevision prepare(Long id, RuleDesignerCompileRequest request) {
        RuleRevision revision = lifecycleService.prepareDesignerDraft(id, request.getSourceType(), request.getSourceId());
        revision.setModelJson(request.getModelJson());
        return revision;
    }
    protected RuleRevision editable(Long id, Long revisionId) { return lifecycleService.requireEditableDraft(id, revisionId); }
    protected RulePreflightReport preview(RuleRevision revision) { return preflightService.validatePreview(revision); }
    protected void insertDraft(RuleRevision revision) { lifecycleService.insertDesignerDraft(revision); }
    protected RuleDraftSaveResponse saveDraft(RuleDraftSaveRequest request) { return draftService.save(request); }
    protected void recordSave(RuleRevision revision, String mode) { lifecycleService.recordDesignerSave(revision, mode); }
    protected String actor() { return operatorResolver.resolve(); }
    protected RuleDesignerSaveOperation findOperation(Long definitionId, String key) {
        return operationMapper.selectOne(new LambdaQueryWrapper<RuleDesignerSaveOperation>()
                .eq(RuleDesignerSaveOperation::getDefinitionId, definitionId)
                .eq(RuleDesignerSaveOperation::getRequestId, key));
    }
    protected void insertOperation(RuleDesignerSaveOperation operation) {
        if (operationMapper.insert(operation) != 1) throw new IllegalStateException("保存幂等记录失败");
    }
}
