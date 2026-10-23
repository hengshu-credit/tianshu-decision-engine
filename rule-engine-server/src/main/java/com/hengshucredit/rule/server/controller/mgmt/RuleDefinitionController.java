package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RuleQueryDTO;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.dto.RuleDraftSourceRequest;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.dto.RuleRevisionRepairRequest;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.governance.GovernedProjectionMutation;
import com.hengshucredit.rule.server.service.RuleCompileService;
import com.hengshucredit.rule.server.service.RuleCallCycleService;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleDraftService;
import com.hengshucredit.rule.server.service.RuleExecuteService;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import com.hengshucredit.rule.server.service.RuleGovernanceSubmissionService;
import com.hengshucredit.rule.server.service.RuleArtifactMigrationService;
import com.hengshucredit.rule.server.service.RuleReferenceIntegrityService;
import com.hengshucredit.rule.server.service.RuleRevisionRepairService;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/definition")
public class RuleDefinitionController {

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleDraftService ruleDraftService;

    @Resource
    private RuleCompileService compileService;

    @Resource
    private RuleExecuteService executeService;

    @Resource
    private RuleCallCycleService ruleCallCycleService;

    @Resource
    private RuleReferenceIntegrityService referenceIntegrityService;

    @Resource
    private RuleLifecycleService lifecycleService;

    @Resource
    private RuleArtifactMigrationService artifactMigrationService;

    @Resource
    private RuleRevisionRepairService revisionRepairService;

    @Resource
    private com.hengshucredit.rule.server.artifact.RuleRevisionFieldViewService revisionFieldViewService;
    @Resource
    private com.hengshucredit.rule.server.service.RuleReferenceCatalogService referenceCatalogService;

    @GetMapping("/{definitionId}/reference-catalog")
    @RequirePermission("rule:view")
    public ApiResponse<com.hengshucredit.rule.model.dto.RuleReferenceCatalog> referenceCatalog(
            @PathVariable Long definitionId, @RequestParam String sourceType, @RequestParam String sourceId) {
        return ApiResponse.ok(referenceCatalogService.getCatalog(definitionId, sourceType, sourceId));
    }

    @Autowired(required = false)
    private RuleGovernanceSubmissionService
            governanceSubmissionService;

    @GetMapping("/list")
    public ApiResponse<IPage<RuleDefinition>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String publishedVersion,
            @RequestParam(required = false) String createBeginTime,
            @RequestParam(required = false) String createEndTime,
            @RequestParam(required = false) String updateBeginTime,
            @RequestParam(required = false) String updateEndTime,
            @RequestParam(required = false) String keyword) {
        RuleQueryDTO query = new RuleQueryDTO();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setProjectId(projectId);
        query.setModelType(modelType);
        query.setKeyword(keyword);
        query.setProjectName(projectName);
        query.setScope(scope);
        query.setStatus(status);
        query.setRuleCode(ruleCode);
        query.setRuleName(ruleName);
        query.setProjectCode(projectCode);
        query.setPublishedVersion(publishedVersion);
        query.setCreateBeginTime(createBeginTime);
        query.setCreateEndTime(createEndTime);
        query.setUpdateBeginTime(updateBeginTime);
        query.setUpdateEndTime(updateEndTime);
        return ApiResponse.ok(definitionService.pageList(query));
    }

    /**
     * 获取全局规则列表（用于添加到项目）
     */
    @GetMapping("/global-list")
    public ApiResponse<IPage<RuleDefinition>> globalList(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String createBeginTime,
            @RequestParam(required = false) String createEndTime,
            @RequestParam(required = false) String updateBeginTime,
            @RequestParam(required = false) String updateEndTime,
            @RequestParam(required = false) String keyword) {
        RuleQueryDTO query = new RuleQueryDTO();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setProjectId(0L);
        query.setModelType(modelType);
        query.setKeyword(keyword);
        query.setScope("GLOBAL");
        query.setRuleCode(ruleCode);
        query.setRuleName(ruleName);
        query.setCreateBeginTime(createBeginTime);
        query.setCreateEndTime(createEndTime);
        query.setUpdateBeginTime(updateBeginTime);
        query.setUpdateEndTime(updateEndTime);
        return ApiResponse.ok(definitionService.pageList(query));
    }

    /**
     * 获取项目规则列表（包含项目级规则和已关联的全局规则）
     */
    @GetMapping("/project-list/{projectId}")
    public ApiResponse<IPage<RuleDefinition>> projectList(
            @PathVariable Long projectId,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String createBeginTime,
            @RequestParam(required = false) String createEndTime,
            @RequestParam(required = false) String updateBeginTime,
            @RequestParam(required = false) String updateEndTime,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(definitionService.pageListForProject(pageNum, pageSize, projectId, modelType, keyword, scope, status, ruleCode, ruleName, createBeginTime, createEndTime, updateBeginTime, updateEndTime));
    }

    @GetMapping("/{id}")
    public ApiResponse<RuleDefinition> get(@PathVariable Long id) {
        return ApiResponse.ok(definitionService.getById(id));
    }

    @PostMapping
    public ApiResponse<RuleDefinition> create(@RequestBody RuleDefinition definition) {
        return ApiResponse.ok(definitionService.createWithContent(definition));
    }

    @PutMapping
    public ApiResponse<Void> update(@RequestBody RuleDefinition definition) {
        definitionService.updateWithProjectInfo(definition);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @GovernedProjectionMutation
    public ApiResponse<Void> delete(@PathVariable Long id) {
        definitionService.deleteWithContent(id);
        return ApiResponse.ok();
    }

    @GetMapping("/content/{definitionId}")
    public ApiResponse<RuleDefinitionContent> getContent(@PathVariable Long definitionId) {
        return ApiResponse.ok(definitionService.getContent(definitionId));
    }

    @PostMapping("/save")
    public ApiResponse<RuleDraftSaveResponse> saveContent(
            @RequestBody(required = false)
            RuleDraftSaveRequest request) {
        requireSaveRequest(request, null);
        request.setModelJson(normalizeModelJson(request.getModelJson()));
        return ApiResponse.ok(ruleDraftService.save(request));
    }

    @PostMapping("/compile/{definitionId}")
    public ApiResponse<CompileResult> compile(@PathVariable Long definitionId) {
        return ApiResponse.ok(compileService.compile(definitionId));
    }

    @PostMapping("/validateCallCycle/{definitionId}")
    public ApiResponse<Map<String, Object>> validateCallCycle(@PathVariable Long definitionId,
                                                    @RequestBody(required = false) String modelJson) {
        String jsonToCheck = normalizeModelJson(modelJson);
        if (jsonToCheck == null || jsonToCheck.isEmpty()) {
            RuleDefinitionContent content = definitionService.getContent(definitionId);
            if (content == null || content.getModelJson() == null) {
                return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "规则内容不存在");
            }
            jsonToCheck = content.getModelJson();
        }
        String error = ruleCallCycleService.validateNoCycle(definitionId, jsonToCheck);
        Map<String, Object> result = new HashMap<>();
        result.put("valid", error == null);
        if (error != null) {
            result.put("message", error);
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/execute")
    public ApiResponse<RuleResult> execute(@RequestBody Map<String, Object> body) {
        Long definitionId = Long.valueOf(body.get("definitionId").toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.get("params") != null
                ? (Map<String, Object>) body.get("params")
                : Collections.emptyMap();
        String modelJson = body.get("modelJson") == null ? null
                : normalizeModelJson(body.get("modelJson").toString());
        Long executionProjectId = optionalPositiveLong(body.get("projectId"));
        com.hengshucredit.rule.model.dto.RuleTestExecutionSource source =
                com.hengshucredit.rule.model.dto.RuleTestExecutionSource.from(body.get("executionSource"));
        if (source != null) {
            source.validate(modelJson);
            String modelType = body.get("modelType") == null ? null : body.get("modelType").toString();
            return ApiResponse.ok(executeService.testExecuteTarget(executeService.resolveTestTarget(
                    definitionId, source, modelJson, modelType, executionProjectId), params));
        }
        if (modelJson != null && !modelJson.trim().isEmpty()) {
            String modelType = body.get("modelType") == null ? null : body.get("modelType").toString();
            return ApiResponse.ok(executeService.testExecutePreview(
                    definitionId, modelJson, modelType, params,
                    executionProjectId));
        }
        return ApiResponse.ok(executeService.testExecute(
                definitionId, params, executionProjectId));
    }

    private Long optionalPositiveLong(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        Long result = Long.valueOf(value.toString());
        if (result <= 0) {
            throw new IllegalArgumentException("项目 ID 必须为正整数");
        }
        return result;
    }

    @PostMapping("/publish/{definitionId}")
    public ApiResponse<RuleRevision> publish(
            @PathVariable Long definitionId,
            @RequestBody(required = false) Map<String, Object> body) {
        RuleLifecycleActionRequest request =
                new RuleLifecycleActionRequest();
        Object changeLog = body == null ? null : body.get("changeLog");
        request.setComment(changeLog == null
                ? null : changeLog.toString());
        return ApiResponse.ok(lifecycleService.publishApproved(
                definitionId, request));
    }

    @PostMapping("/unpublish/{definitionId}")
    public ApiResponse<RuleRevision> unpublish(
            @PathVariable Long definitionId) {
        return ApiResponse.ok(lifecycleService.offlinePublished(
                definitionId, new RuleLifecycleActionRequest()));
    }

    @PostMapping("/{definitionId}/revisions/draft")
    public ApiResponse<RuleRevision> createDraft(
            @PathVariable Long definitionId,
            @RequestBody(required = false) Map<String, Long> body) {
        Long baseRevisionId =
                body == null ? null : body.get("baseRevisionId");
        return ApiResponse.ok(lifecycleService.createDraft(
                definitionId, baseRevisionId));
    }

    @PostMapping("/{definitionId}/revisions/draft/from-source")
    public ApiResponse<RuleDraftSaveResponse> createDraftFromSource(
            @PathVariable Long definitionId,
            @RequestBody(required = false) RuleDraftSourceRequest request) {
        return ApiResponse.ok(lifecycleService.createDraftFromSource(
                definitionId, request));
    }

    @PostMapping("/{definitionId}/revisions/temporary-draft")
    public ApiResponse<RuleDraftSaveResponse> saveTemporaryDraft(
            @PathVariable Long definitionId, @RequestBody RuleDraftSourceRequest request) {
        return ApiResponse.ok(definitionService.saveTemporaryDraft(definitionId, request));
    }

    @GetMapping("/{definitionId}/revisions/repair-preview")
    public ApiResponse<RuleRevisionRepairService.RepairPreview>
    repairPreview(@PathVariable Long definitionId) {
        return ApiResponse.ok(revisionRepairService.preview(definitionId));
    }

    @PostMapping("/{definitionId}/revisions/repair")
    public ApiResponse<RuleRevision> repairRevision(
            @PathVariable Long definitionId,
            @RequestBody(required = false)
            RuleRevisionRepairRequest request) {
        try {
            return ApiResponse.ok(revisionRepairService.repair(
                    definitionId, request));
        } catch (RuleGovernanceException error) {
            throw error;
        } catch (RuleRevisionRepairService
                .RepairPreviewChangedException error) {
            RuleValidationIssue issue =
                    RuleValidationIssue.error(
                            "REPAIR_PREVIEW_CHANGED", "$",
                            error.getMessage())
                            .withRevisionId(request == null
                                    ? null
                                    : request.getSourceRevisionId());
            throw new RuleGovernanceException(
                    409, "REPAIR_PREVIEW_CHANGED",
                    error.getMessage(),
                    Collections.singletonList(issue));
        }
    }

    @PostMapping("/migrate-artifacts")
    public ApiResponse<RuleArtifactMigrationService.MigrationReport> migrateLegacyArtifacts() {
        return ApiResponse.ok(artifactMigrationService.migrateAll());
    }

    @GetMapping("/{definitionId}/revisions")
    public ApiResponse<List<RuleRevision>> listRevisions(@PathVariable Long definitionId) {
        return ApiResponse.ok(lifecycleService.listRevisions(definitionId));
    }

    @GetMapping("/{definitionId}/revisions/current-draft")
    public ApiResponse<RuleRevision> currentDraft(@PathVariable Long definitionId) {
        return ApiResponse.ok(lifecycleService.currentDraft(definitionId));
    }

    @GetMapping("/{definitionId}/revisions/{revisionId}")
    public ApiResponse<RuleRevision> getRevision(@PathVariable Long definitionId,
                                       @PathVariable Long revisionId) {
        try {
            return ApiResponse.ok(lifecycleService.getRevision(definitionId, revisionId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, e.getMessage());
        }
    }

    @GetMapping("/{definitionId}/revisions/{revisionId}/fields")
    @RequirePermission("rule:view")
    public ApiResponse<com.hengshucredit.rule.model.dto.RuleRevisionFieldView> getRevisionFields(
            @PathVariable Long definitionId, @PathVariable Long revisionId) {
        try {
            return ApiResponse.ok(revisionFieldViewService.getFields(definitionId, revisionId));
        } catch (IllegalArgumentException error) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, error.getMessage());
        }
    }

    @PostMapping("/{definitionId}/revisions/{revisionId}/preflight")
    public ApiResponse<RulePreflightReport> preflight(@PathVariable Long definitionId,
                                            @PathVariable Long revisionId) {
        try {
            RulePreflightReport report = lifecycleService.preflightReport(definitionId, revisionId);
            return report.isValid() ? ApiResponse.ok(report) : validationFailure(report);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, e.getMessage());
        }
    }

    @PostMapping("/{definitionId}/revisions/{revisionId}/submit")
    @RequirePermission("approval:submit")
    public ApiResponse<RuleRevision> submitRevision(@PathVariable Long definitionId,
                                          @PathVariable Long revisionId,
                                          @RequestBody(required = false)
                                          RuleLifecycleActionRequest request) {
        return lifecycleAction(definitionId, revisionId,
                () -> governanceSubmissionService == null
                        ? lifecycleService.submit(revisionId, request)
                        : governanceSubmissionService.submit(
                        revisionId, request));
    }

    @PostMapping("/{definitionId}/revisions/{revisionId}/return")
    public ApiResponse<RuleRevision> returnRevision(@PathVariable Long definitionId,
                                          @PathVariable Long revisionId,
                                          @RequestBody(required = false)
                                          RuleLifecycleActionRequest request) {
        return lifecycleAction(definitionId, revisionId,
                () -> unmanagedLifecycleAction(definitionId, revisionId,
                        () -> lifecycleService.returnToDraft(
                                revisionId, request)));
    }

    @PostMapping("/{definitionId}/revisions/{revisionId}/reject")
    public ApiResponse<RuleRevision> rejectRevision(@PathVariable Long definitionId,
                                          @PathVariable Long revisionId,
                                          @RequestBody(required = false)
                                          RuleLifecycleActionRequest request) {
        return lifecycleAction(definitionId, revisionId,
                () -> unmanagedLifecycleAction(definitionId, revisionId,
                        () -> lifecycleService.returnToDraft(
                                revisionId, request)));
    }

    @PostMapping("/{definitionId}/revisions/{revisionId}/approve")
    public ApiResponse<RuleRevision> approveRevision(@PathVariable Long definitionId,
                                           @PathVariable Long revisionId,
                                           @RequestBody(required = false)
                                           RuleLifecycleActionRequest request) {
        return lifecycleAction(definitionId, revisionId,
                () -> unmanagedLifecycleAction(definitionId, revisionId,
                        () -> lifecycleService.approve(
                                revisionId, request)));
    }

    @PostMapping("/{definitionId}/revisions/{revisionId}/publish")
    public ApiResponse<RuleRevision> publishRevision(@PathVariable Long definitionId,
                                           @PathVariable Long revisionId,
                                           @RequestBody(required = false)
                                           RuleLifecycleActionRequest request) {
        return lifecycleAction(definitionId, revisionId,
                () -> unmanagedLifecycleAction(definitionId, revisionId,
                        () -> lifecycleService.publish(
                                revisionId, request)));
    }

    @PostMapping("/{definitionId}/revisions/{revisionId}/offline")
    public ApiResponse<RuleRevision> offlineRevision(@PathVariable Long definitionId,
                                           @PathVariable Long revisionId,
                                           @RequestBody(required = false)
                                           RuleLifecycleActionRequest request) {
        return lifecycleAction(definitionId, revisionId,
                () -> unmanagedLifecycleAction(definitionId, revisionId,
                        () -> lifecycleService.offline(
                                revisionId, request)));
    }

    @GetMapping("/{definitionId}/revisions/timeline")
    public ApiResponse<List<RuleLifecycleEvent>> lifecycleTimeline(@PathVariable Long definitionId) {
        return ApiResponse.ok(lifecycleService.timeline(definitionId));
    }

    private ApiResponse<RuleRevision> lifecycleAction(Long definitionId, Long revisionId,
                                            java.util.function.Supplier<RuleRevision> action) {
        try {
            lifecycleService.getRevision(definitionId, revisionId);
            return ApiResponse.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, e.getMessage());
        } catch (IllegalStateException e) {
            com.hengshucredit.rule.model.PlatformCode code = e.getMessage() != null && e.getMessage().startsWith("发布前验证未通过")
                    ? com.hengshucredit.rule.model.PlatformCode.PREFLIGHT_FAILED : com.hengshucredit.rule.model.PlatformCode.GOVERNANCE_CONFLICT;
            return ApiResponse.fail(code, e.getMessage());
        }
    }

    private RuleRevision unmanagedLifecycleAction(
            Long definitionId,
            Long revisionId,
            java.util.function.Supplier<RuleRevision> action) {
        RuleRevision revision =
                lifecycleService.getRevision(definitionId, revisionId);
        if (revision.getGovernanceRequestId() != null) {
            throw new IllegalStateException(
                    "该规则修订已进入统一审批，请在审批管理中完成操作");
        }
        return action.get();
    }

    private ApiResponse<RulePreflightReport> validationFailure(RulePreflightReport report) {
        ApiResponse<RulePreflightReport> response = ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PREFLIGHT_FAILED, null);
        response.setData(report);
        return response;
    }

    @GetMapping("/versions/{definitionId}")
    public ApiResponse<List<RuleDefinitionVersion>> listVersions(@PathVariable Long definitionId) {
        return ApiResponse.ok(definitionService.listVersions(definitionId));
    }

    @GetMapping("/{definitionId}/versions/{versionId}")
    public ApiResponse<RuleDefinitionVersion> getVersionById(
            @PathVariable Long definitionId,
            @PathVariable Long versionId) {
        RuleDefinitionVersion snapshot = definitionService
                .getVersionById(definitionId, versionId);
        return snapshot == null
                ? ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, "版本快照不存在或不属于当前规则")
                : ApiResponse.ok(snapshot);
    }

    @GetMapping("/version/{definitionId}/{version}")
    public ApiResponse<RuleDefinitionVersion> getVersion(@PathVariable Long definitionId,
                                               @PathVariable Integer version) {
        RuleDefinitionVersion snapshot = definitionService.getVersion(definitionId, version);
        return snapshot == null ? ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "Version not found") : ApiResponse.ok(snapshot);
    }

    @GetMapping("/versionCompare/{definitionId}")
    public ApiResponse<Map<String, Object>> compareVersions(@PathVariable Long definitionId,
                                                  @RequestParam Integer leftVersion,
                                                  @RequestParam Integer rightVersion) {
        try {
            return ApiResponse.ok(definitionService.compareVersions(definitionId, leftVersion, rightVersion));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    @PostMapping("/rollback/{definitionId}/{version}")
    public ApiResponse<Void> rollback(@PathVariable Long definitionId, @PathVariable Integer version) {
        try {
            definitionService.rollbackToVersion(definitionId, version);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    @PostMapping("/script/{definitionId:\\d+}")
    public ApiResponse<RuleDraftSaveResponse> saveScript(
            @PathVariable Long definitionId,
            @RequestBody(required = false)
            RuleDraftSaveRequest request) {
        requireSaveRequest(request, definitionId);
        request.setModelJson(
                normalizeModelJson(request.getModelJson()));
        return ApiResponse.ok(definitionService.saveScript(request));
    }

    /**
     * 更新编辑模式（visual/script）
     * 请求体: { "scriptMode": "visual" | "script" }
     */
    @PostMapping("/scriptMode/{definitionId:\\d+}")
    public ApiResponse<Void> updateScriptMode(@PathVariable Long definitionId,
                                     @RequestBody Map<String, String> body) {
        String mode = body.get("scriptMode");
        if (mode == null || (!"visual".equals(mode) && !"script".equals(mode))) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, "scriptMode 必须为 visual 或 script");
        }
        definitionService.updateScriptMode(definitionId, mode);
        return ApiResponse.ok();
    }

    /**
     * 脚本模式下验证脚本语法（不覆盖可视化模型编译结果）
     * 请求体: { "script": "..." }
     */
    @PostMapping("/validateScript/{definitionId:\\d+}")
    public ApiResponse<CompileResult> validateScript(@PathVariable Long definitionId,
                                            @RequestBody Map<String, String> body) {
        String script = body.get("script");
        if (script == null || script.trim().isEmpty()) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUIRED_FIELD_MISSING, "脚本内容不能为空");
        }
        return ApiResponse.ok(compileService.validateScript(script.trim()));
    }

    // ========== 规则字段管理 ==========

    /**
     * 获取规则详情（含输入输出字段）
     */
    @GetMapping("/detail/{id}")
    public ApiResponse<RuleDefinition> getDetail(@PathVariable Long id) {
        return ApiResponse.ok(definitionService.getDetail(id));
    }

    /**
     * 获取规则输入字段列表
     */
    @GetMapping("/inputFields/{definitionId}")
    public ApiResponse<List<RuleDefinitionInputField>> listInputFields(@PathVariable Long definitionId) {
        return ApiResponse.ok(definitionService.listInputFields(definitionId));
    }

    /**
     * 获取规则输出字段列表
     */
    @GetMapping("/outputFields/{definitionId}")
    public ApiResponse<List<RuleDefinitionOutputField>> listOutputFields(@PathVariable Long definitionId) {
        return ApiResponse.ok(definitionService.listOutputFields(definitionId));
    }

    /**
     * 更新规则输入字段（关联变量映射）
     */
    @PutMapping("/inputField/{fieldId:\\d+}")
    public ApiResponse<Void> updateInputField(@PathVariable Long fieldId, @RequestBody RuleDefinitionInputField field) {
        definitionService.updateInputField(fieldId, field);
        return ApiResponse.ok();
    }

    /**
     * 更新规则输出字段（关联变量映射）
     */
    @PutMapping("/outputField/{fieldId:\\d+}")
    public ApiResponse<Void> updateOutputField(@PathVariable Long fieldId, @RequestBody RuleDefinitionOutputField field) {
        definitionService.updateOutputField(fieldId, field);
        return ApiResponse.ok();
    }


    @PostMapping("/refreshFields/{definitionId}")
    public ApiResponse<RuleDraftSaveResponse> refreshFields(
            @PathVariable Long definitionId,
            @RequestBody(required = false)
            RuleDraftSaveRequest request) {
        requireSaveRequest(request, definitionId);
        request.setModelJson(
                normalizeModelJson(request.getModelJson()));
        return ApiResponse.ok(definitionService.refreshFields(request));
    }

    private void requireSaveRequest(
            RuleDraftSaveRequest request, Long pathDefinitionId) {
        if (request == null) {
            throw governance(400, "DRAFT_SAVE_REQUEST_REQUIRED",
                    "保存请求不能为空");
        }
        if (pathDefinitionId != null
                && !pathDefinitionId.equals(
                request.getDefinitionId())) {
            throw governance(400,
                    "DRAFT_SAVE_CONTRACT_INVALID",
                    "路径中的规则 ID 与保存请求不一致");
        }
    }

    private RuleGovernanceException governance(
            int status, String code, String message) {
        return new RuleGovernanceException(
                status, code, message,
                Collections.singletonList(
                        RuleValidationIssue.error(
                                code, "$", message)));
    }

    private String normalizeModelJson(String modelJson) {
        if (modelJson == null) {
            return null;
        }
        String json = modelJson.trim();
        if (json.startsWith("modelJson=")) {
            json = json.substring("modelJson=".length());
        }
        if (json.contains("%7B") || json.contains("%7b")
                || json.contains("%5B") || json.contains("%5b")
                || json.contains("%22")) {
            try {
                json = URLDecoder.decode(json, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                throw new IllegalArgumentException("模型 JSON URL 解码失败: " + e.getMessage(), e);
            }
        }
        if (json.startsWith("\"") && json.endsWith("\"")) {
            try {
                Object decoded = com.alibaba.fastjson.JSON.parse(json);
                if (decoded instanceof String) {
                    json = ((String) decoded).trim();
                }
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("模型 JSON 字符串解码失败: " + e.getMessage(), e);
            }
        } else if (json.startsWith("'") && json.endsWith("'")) {
            json = json.substring(1, json.length() - 1);
        }
        return json;
    }

    @GetMapping("/reference-integrity/scan/{definitionId}")
    public ApiResponse<RuleReferenceIntegrityService.AuditReport> scanReferenceIntegrity(
            @PathVariable Long definitionId) {
        try {
            return ApiResponse.ok(referenceIntegrityService.scan(definitionId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    @GetMapping("/reference-integrity/scan-all")
    public ApiResponse<List<RuleReferenceIntegrityService.AuditReport>> scanAllReferenceIntegrity() {
        return ApiResponse.ok(referenceIntegrityService.scanAll());
    }

    @PostMapping("/reference-integrity/migrate")
    public ApiResponse<RuleReferenceIntegrityService.MigrationResult> migrateReferences(
            @RequestBody RuleReferenceIntegrityService.MigrationRequest request) {
        try {
            return ApiResponse.ok(referenceIntegrityService.migrate(request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }
}
