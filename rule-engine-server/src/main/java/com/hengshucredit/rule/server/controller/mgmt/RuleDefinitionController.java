package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RuleQueryDTO;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleCompileService;
import com.hengshucredit.rule.server.service.RuleCallCycleService;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleExecuteService;
import com.hengshucredit.rule.server.service.RulePublishService;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import com.hengshucredit.rule.server.service.RuleArtifactMigrationService;
import com.hengshucredit.rule.server.service.RuleReferenceIntegrityService;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
    private RuleCompileService compileService;

    @Resource
    private RuleExecuteService executeService;

    @Resource
    private RulePublishService publishService;

    @Resource
    private RuleCallCycleService ruleCallCycleService;

    @Resource
    private RuleReferenceIntegrityService referenceIntegrityService;

    @Resource
    private RuleLifecycleService lifecycleService;

    @Resource
    private RuleArtifactMigrationService artifactMigrationService;

    @GetMapping("/list")
    public R<IPage<RuleDefinition>> list(
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
        return R.ok(definitionService.pageList(query));
    }

    /**
     * 获取全局规则列表（用于添加到项目）
     */
    @GetMapping("/global-list")
    public R<IPage<RuleDefinition>> globalList(
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
        return R.ok(definitionService.pageList(query));
    }

    /**
     * 将全局规则添加到项目
     */
    @PostMapping("/add-global-to-project")
    public R<RuleDefinitionRef> addGlobalToProject(@RequestBody Map<String, String> body) {
        String pidStr = body.get("projectId");
        String didStr = body.get("definitionId");
        Long projectId = pidStr != null && !pidStr.isEmpty() ? Long.valueOf(pidStr) : null;
        Long definitionId = didStr != null && !didStr.isEmpty() ? Long.valueOf(didStr) : null;
        if (definitionId == null || projectId == null) {
            return R.fail("definitionId 和 projectId 不能为空");
        }
        try {
            RuleDefinitionRef ref = definitionService.addGlobalRuleToProject(definitionId, projectId);
            return R.ok(ref);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取项目规则列表（包含项目级规则和已关联的全局规则）
     */
    @GetMapping("/project-list/{projectId}")
    public R<IPage<RuleDefinition>> projectList(
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
        return R.ok(definitionService.pageListForProject(pageNum, pageSize, projectId, modelType, keyword, scope, status, ruleCode, ruleName, createBeginTime, createEndTime, updateBeginTime, updateEndTime));
    }

    @GetMapping("/{id}")
    public R<RuleDefinition> get(@PathVariable Long id) {
        return R.ok(definitionService.getById(id));
    }

    @PostMapping
    public R<RuleDefinition> create(@RequestBody RuleDefinition definition) {
        return R.ok(definitionService.createWithContent(definition));
    }

    @PutMapping
    public R<Void> update(@RequestBody RuleDefinition definition) {
        definitionService.updateWithProjectInfo(definition);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        definitionService.deleteWithContent(id);
        return R.ok();
    }

    @GetMapping("/content/{definitionId}")
    public R<RuleDefinitionContent> getContent(@PathVariable Long definitionId) {
        return R.ok(definitionService.getContent(definitionId));
    }

    @PostMapping("/save")
    public R<Void> saveContent(@RequestBody Map<String, Object> body) {
        Long definitionId = Long.valueOf(body.get("definitionId").toString());
        String modelJson = normalizeModelJson(body.get("modelJson").toString());
        if (body.containsKey("openApiConfigJson")) {
            Object config = body.get("openApiConfigJson");
            String configJson = config == null ? null
                    : (config instanceof String ? (String) config : com.alibaba.fastjson.JSON.toJSONString(config));
            definitionService.saveContent(definitionId, modelJson, configJson);
        } else {
            definitionService.saveContent(definitionId, modelJson);
        }
        return R.ok();
    }

    @PostMapping("/compile/{definitionId}")
    public R<CompileResult> compile(@PathVariable Long definitionId) {
        return R.ok(compileService.compile(definitionId));
    }

    @PostMapping("/validateCallCycle/{definitionId}")
    public R<Map<String, Object>> validateCallCycle(@PathVariable Long definitionId,
                                                    @RequestBody(required = false) String modelJson) {
        String jsonToCheck = normalizeModelJson(modelJson);
        if (jsonToCheck == null || jsonToCheck.isEmpty()) {
            RuleDefinitionContent content = definitionService.getContent(definitionId);
            if (content == null || content.getModelJson() == null) {
                return R.fail("规则内容不存在");
            }
            jsonToCheck = content.getModelJson();
        }
        String error = ruleCallCycleService.validateNoCycle(definitionId, jsonToCheck);
        Map<String, Object> result = new HashMap<>();
        result.put("valid", error == null);
        if (error != null) {
            result.put("message", error);
        }
        return R.ok(result);
    }

    @PostMapping("/execute")
    public R<RuleResult> execute(@RequestBody Map<String, Object> body) {
        Long definitionId = Long.valueOf(body.get("definitionId").toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.get("params") != null
                ? (Map<String, Object>) body.get("params")
                : Collections.emptyMap();
        String modelJson = body.get("modelJson") == null ? null
                : normalizeModelJson(body.get("modelJson").toString());
        if (modelJson != null && !modelJson.trim().isEmpty()) {
            String modelType = body.get("modelType") == null ? null : body.get("modelType").toString();
            return R.ok(executeService.testExecutePreview(definitionId, modelJson, modelType, params));
        }
        return R.ok(executeService.testExecute(definitionId, params));
    }

    @PostMapping("/publish/{definitionId}")
    public R<Void> publish(@PathVariable Long definitionId, @RequestBody(required = false) Map<String, Object> body) {
        String changeLog = body != null ? (String) body.get("changeLog") : null;
        String error = publishService.publish(definitionId, changeLog);
        return error == null ? R.ok() : R.fail(error);
    }

    @PostMapping("/unpublish/{definitionId}")
    public R<Void> unpublish(@PathVariable Long definitionId) {
        String error = publishService.unpublish(definitionId);
        return error == null ? R.ok() : R.fail(error);
    }

    @PostMapping("/{definitionId}/revisions/draft")
    public R<RuleRevision> ensureDraft(@PathVariable Long definitionId) {
        try {
            return R.ok(lifecycleService.ensureDraft(definitionId));
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return R.fail(409, e.getMessage());
        }
    }

    @PostMapping("/migrate-artifacts")
    public R<RuleArtifactMigrationService.MigrationReport> migrateLegacyArtifacts() {
        return R.ok(artifactMigrationService.migrateAll());
    }

    @GetMapping("/{definitionId}/revisions")
    public R<List<RuleRevision>> listRevisions(@PathVariable Long definitionId) {
        return R.ok(lifecycleService.listRevisions(definitionId));
    }

    @GetMapping("/{definitionId}/revisions/current-draft")
    public R<RuleRevision> currentDraft(@PathVariable Long definitionId) {
        return R.ok(lifecycleService.currentDraft(definitionId));
    }

    @GetMapping("/{definitionId}/revisions/{revisionId}")
    public R<RuleRevision> getRevision(@PathVariable Long definitionId,
                                       @PathVariable Long revisionId) {
        try {
            return R.ok(lifecycleService.getRevision(definitionId, revisionId));
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }

    @PostMapping("/{definitionId}/revisions/{revisionId}/preflight")
    public R<RulePreflightReport> preflight(@PathVariable Long definitionId,
                                            @PathVariable Long revisionId) {
        try {
            RulePreflightReport report = lifecycleService.preflightReport(definitionId, revisionId);
            return report.isValid() ? R.ok(report) : validationFailure(report);
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }

    @PostMapping("/{definitionId}/revisions/{revisionId}/submit")
    public R<RuleRevision> submitRevision(@PathVariable Long definitionId,
                                          @PathVariable Long revisionId,
                                          @RequestBody(required = false)
                                          RuleLifecycleActionRequest request) {
        return lifecycleAction(definitionId, revisionId,
                () -> lifecycleService.submit(revisionId, request));
    }

    @PostMapping("/{definitionId}/revisions/{revisionId}/return")
    public R<RuleRevision> returnRevision(@PathVariable Long definitionId,
                                          @PathVariable Long revisionId,
                                          @RequestBody(required = false)
                                          RuleLifecycleActionRequest request) {
        return lifecycleAction(definitionId, revisionId,
                () -> lifecycleService.returnToDraft(revisionId, request));
    }

    @PostMapping("/{definitionId}/revisions/{revisionId}/approve")
    public R<RuleRevision> approveRevision(@PathVariable Long definitionId,
                                           @PathVariable Long revisionId,
                                           @RequestBody(required = false)
                                           RuleLifecycleActionRequest request) {
        return lifecycleAction(definitionId, revisionId,
                () -> lifecycleService.approve(revisionId, request));
    }

    @PostMapping("/{definitionId}/revisions/{revisionId}/publish")
    public R<RuleRevision> publishRevision(@PathVariable Long definitionId,
                                           @PathVariable Long revisionId,
                                           @RequestBody(required = false)
                                           RuleLifecycleActionRequest request) {
        return lifecycleAction(definitionId, revisionId,
                () -> lifecycleService.publish(revisionId, request));
    }

    @PostMapping("/{definitionId}/revisions/{revisionId}/offline")
    public R<RuleRevision> offlineRevision(@PathVariable Long definitionId,
                                           @PathVariable Long revisionId,
                                           @RequestBody(required = false)
                                           RuleLifecycleActionRequest request) {
        return lifecycleAction(definitionId, revisionId,
                () -> lifecycleService.offline(revisionId, request));
    }

    @GetMapping("/{definitionId}/revisions/timeline")
    public R<List<RuleLifecycleEvent>> lifecycleTimeline(@PathVariable Long definitionId) {
        return R.ok(lifecycleService.timeline(definitionId));
    }

    private R<RuleRevision> lifecycleAction(Long definitionId, Long revisionId,
                                            java.util.function.Supplier<RuleRevision> action) {
        try {
            lifecycleService.getRevision(definitionId, revisionId);
            return R.ok(action.get());
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            int code = e.getMessage() != null && e.getMessage().startsWith("发布前验证未通过")
                    ? 422 : 409;
            return R.fail(code, e.getMessage());
        }
    }

    private R<RulePreflightReport> validationFailure(RulePreflightReport report) {
        R<RulePreflightReport> response = R.fail(422, "发布前验证未通过");
        response.setData(report);
        return response;
    }

    @GetMapping("/versions/{definitionId}")
    public R<List<RuleDefinitionVersion>> listVersions(@PathVariable Long definitionId) {
        return R.ok(definitionService.listVersions(definitionId));
    }

    @GetMapping("/version/{definitionId}/{version}")
    public R<RuleDefinitionVersion> getVersion(@PathVariable Long definitionId,
                                               @PathVariable Integer version) {
        RuleDefinitionVersion snapshot = definitionService.getVersion(definitionId, version);
        return snapshot == null ? R.fail("Version not found") : R.ok(snapshot);
    }

    @GetMapping("/versionCompare/{definitionId}")
    public R<Map<String, Object>> compareVersions(@PathVariable Long definitionId,
                                                  @RequestParam Integer leftVersion,
                                                  @RequestParam Integer rightVersion) {
        try {
            return R.ok(definitionService.compareVersions(definitionId, leftVersion, rightVersion));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/rollback/{definitionId}/{version}")
    public R<Void> rollback(@PathVariable Long definitionId, @PathVariable Integer version) {
        try {
            definitionService.rollbackToVersion(definitionId, version);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 技术人员直接保存脚本（脚本模式），跳过可视化编译器。
     * 请求体: { "script": "..." }
     */
    @PostMapping("/script/{definitionId:\\d+}")
    public R<Void> saveScript(@PathVariable Long definitionId,
                               @RequestBody Map<String, String> body) {
        String script = body.get("script");
        if (script == null || script.trim().isEmpty()) {
            return R.fail("脚本内容不能为空");
        }
        definitionService.saveScript(definitionId, script.trim());
        return R.ok();
    }

    /**
     * 更新编辑模式（visual/script）
     * 请求体: { "scriptMode": "visual" | "script" }
     */
    @PostMapping("/scriptMode/{definitionId:\\d+}")
    public R<Void> updateScriptMode(@PathVariable Long definitionId,
                                     @RequestBody Map<String, String> body) {
        String mode = body.get("scriptMode");
        if (mode == null || (!"visual".equals(mode) && !"script".equals(mode))) {
            return R.fail("scriptMode 必须为 visual 或 script");
        }
        definitionService.updateScriptMode(definitionId, mode);
        return R.ok();
    }

    /**
     * 脚本模式下验证脚本语法（不覆盖可视化模型编译结果）
     * 请求体: { "script": "..." }
     */
    @PostMapping("/validateScript/{definitionId:\\d+}")
    public R<CompileResult> validateScript(@PathVariable Long definitionId,
                                            @RequestBody Map<String, String> body) {
        String script = body.get("script");
        if (script == null || script.trim().isEmpty()) {
            return R.fail("脚本内容不能为空");
        }
        return R.ok(compileService.validateScript(script.trim()));
    }

    // ========== 规则字段管理 ==========

    /**
     * 获取规则详情（含输入输出字段）
     */
    @GetMapping("/detail/{id}")
    public R<RuleDefinition> getDetail(@PathVariable Long id) {
        return R.ok(definitionService.getDetail(id));
    }

    /**
     * 获取规则输入字段列表
     */
    @GetMapping("/inputFields/{definitionId}")
    public R<List<RuleDefinitionInputField>> listInputFields(@PathVariable Long definitionId) {
        return R.ok(definitionService.listInputFields(definitionId));
    }

    /**
     * 获取规则输出字段列表
     */
    @GetMapping("/outputFields/{definitionId}")
    public R<List<RuleDefinitionOutputField>> listOutputFields(@PathVariable Long definitionId) {
        return R.ok(definitionService.listOutputFields(definitionId));
    }

    /**
     * 更新规则输入字段（关联变量映射）
     */
    @PutMapping("/inputField/{fieldId:\\d+}")
    public R<Void> updateInputField(@PathVariable Long fieldId, @RequestBody RuleDefinitionInputField field) {
        definitionService.updateInputField(fieldId, field);
        return R.ok();
    }

    /**
     * 更新规则输出字段（关联变量映射）
     */
    @PutMapping("/outputField/{fieldId:\\d+}")
    public R<Void> updateOutputField(@PathVariable Long fieldId, @RequestBody RuleDefinitionOutputField field) {
        definitionService.updateOutputField(fieldId, field);
        return R.ok();
    }


    /**
     * 刷新规则的输入/输出字段（从当前模型内容解析并持久化）。
     * 支持传入 modelJson（用于设计器保存后立即刷新，避免事务未提交导致的脏读）；
     * 不传 modelJson 时从数据库读取（用于技术人员手动刷新场景）。
     */
    @PostMapping("/refreshFields/{definitionId}")
    public R<RuleDefinition> refreshFields(@PathVariable Long definitionId,
                                          @RequestBody(required = false) String modelJson) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            return R.fail("规则不存在");
        }
        // 优先使用传入的 modelJson（设计器场景）；无传入则从数据库读取（手动刷新场景）
        String jsonToParse = normalizeModelJson(modelJson);
        if (jsonToParse == null || jsonToParse.isEmpty()) {
            RuleDefinitionContent content = definitionService.getContent(definitionId);
            if (content == null || content.getModelJson() == null) {
                return R.fail("规则内容不存在");
            }
            jsonToParse = content.getModelJson();
        }
        definitionService.refreshFields(definitionId, jsonToParse, definition.getModelType());
        return R.ok(definitionService.getDetail(definitionId));
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
    public R<RuleReferenceIntegrityService.AuditReport> scanReferenceIntegrity(
            @PathVariable Long definitionId) {
        try {
            return R.ok(referenceIntegrityService.scan(definitionId));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/reference-integrity/scan-all")
    public R<List<RuleReferenceIntegrityService.AuditReport>> scanAllReferenceIntegrity() {
        return R.ok(referenceIntegrityService.scanAll());
    }

    @PostMapping("/reference-integrity/migrate")
    public R<RuleReferenceIntegrityService.MigrationResult> migrateReferences(
            @RequestBody RuleReferenceIntegrityService.MigrationRequest request) {
        try {
            return R.ok(referenceIntegrityService.migrate(request));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }
}
