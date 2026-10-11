package com.hengshucredit.rule.server.controller;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleModelVersion;
import com.hengshucredit.rule.model.entity.ResourceImpactAnalysis;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.governance.GovernanceRequestView;
import com.hengshucredit.rule.server.governance.GovernedProjectionMutation;
import com.hengshucredit.rule.server.governance.LegacyGovernanceRestoreService;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import com.hengshucredit.rule.server.service.RuleModelService;
import com.hengshucredit.rule.server.service.ResourceImpactAnalysisService;
import com.hengshucredit.rule.server.service.RuleRuntimeCallLogService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/model")
public class RuleModelController {

    @Resource
    private RuleModelService modelService;

    @Resource
    private RuleRuntimeCallLogService runtimeCallLogService;

    @Resource
    private ResourceImpactAnalysisService impactAnalysisService;

    @Resource
    private LegacyGovernanceRestoreService legacyRestoreService;

    @Resource
    private GovernanceApprovalService governanceApprovalService;

    @Resource
    private ConsoleOperatorResolver operatorResolver;

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public R<String> health() {
        return R.ok("ok");
    }

    /**
     * 查询 ONNX Runtime execution provider 能力，供模型配置页面展示。
     */
    @GetMapping("/runtimeCapabilities")
    public R<Map<String, Object>> runtimeCapabilities() {
        return R.ok(modelService.runtimeCapabilities());
    }

    /**
     * 检查模型编码是否与现有模型冲突
     * @param modelCode 模型编码
     * @param scope GLOBAL / PROJECT
     * @param projectId 项目ID（仅 PROJECT 时需要）
     * @param excludeId 排除的模型ID（编辑时传自己的ID，跳过自身比对）
     */
    @GetMapping("/checkCode")
    public R<Boolean> checkCode(
            @RequestParam String modelCode,
            @RequestParam String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long excludeId) {
        boolean exists = modelService.existsModelCodeConflict(modelCode, scope, projectId, excludeId);
        return R.ok(exists);
    }

    /**
     * 上传并解析模型文件
     */
    @PostMapping("/upload")
    @RequirePermission("model:edit")
    public R<GovernanceRequestView> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String scope,
            @RequestParam String modelCode,
            @RequestParam String modelName,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String changeLog,
            @RequestParam(required = false) String testParams,
            @RequestParam(required = false) String onnxTaskType,
            @RequestParam(required = false) String onnxConfig,
            @RequestParam(defaultValue = "0") Integer preloadOnStartup,
            @RequestParam(defaultValue = "120000") Integer executionTimeoutMs) {
        try {
            RuleModel model = modelService.buildUploadSnapshot(
                    file, projectId, scope, modelCode, modelName,
                    modelType, description, testParams,
                    onnxTaskType, onnxConfig,
                    preloadOnStartup, executionTimeoutMs);
            return R.ok(createModelDraft(model, "CREATE",
                    changeLog == null || changeLog.isBlank()
                            ? "上传并新建模型" : changeLog));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 分页查询模型列表
     */
    @GetMapping("/list")
    public R<IPage<RuleModel>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String modelFormat,
            @RequestParam(required = false) String modelCode,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName) {
        return R.ok(modelService.pageList(pageNum, pageSize, projectId, scope,
                modelType, modelFormat, modelCode, modelName, projectCode, projectName));
    }

    /**
     * 获取模型详情（含字段信息）
     */
    @GetMapping("/{id}")
    public R<RuleModel> get(@PathVariable Long id) {
        RuleModel model = modelService.getDetail(id);
        return model != null ? R.ok(model) : R.fail("模型不存在");
    }

    /**
     * 更新模型元信息（不包括文件内容）
     */
    @PutMapping
    @GovernedProjectionMutation
    public R<Void> update(@RequestBody RuleModel model) {
        try {
            modelService.update(model);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 删除模型
     */
    @DeleteMapping("/{id}")
    @GovernedProjectionMutation
    public R<Void> delete(@PathVariable Long id, @RequestParam String impactToken) {
        try {
            modelService.delete(id, impactToken);
            return R.ok();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 发布模型
     */
    @PostMapping("/publish/{id}")
    @GovernedProjectionMutation
    public R<Void> publish(@PathVariable Long id,
            @RequestParam(required = false) String changeLog) {
        try {
            modelService.publish(id, changeLog, null);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 下线模型
     */
    @PostMapping("/unpublish/{id}")
    @GovernedProjectionMutation
    public R<Void> unpublish(@PathVariable Long id, @RequestParam String impactToken) {
        try {
            modelService.unpublish(id, impactToken);
            return R.ok();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/impact/{id}")
    public R<ResourceImpactAnalysis> analyzeImpact(@PathVariable Long id,
                                                   @RequestParam String action) {
        try {
            return R.ok(impactAnalysisService.analyze("MODEL", id, action, null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/replace/{id}")
    @RequirePermission("model:edit")
    public R<GovernanceRequestView> replace(@PathVariable Long id,
                                @RequestParam("file") MultipartFile file,
                                @RequestParam String impactToken,
                                @RequestParam(required = false) String changeLog,
                                @RequestParam(required = false) String testParams,
                                @RequestParam(required = false) String onnxTaskType,
                                @RequestParam(required = false) String onnxConfig) {
        try {
            RuleModel model = modelService.buildReplacementSnapshot(
                    id, file, testParams, onnxTaskType,
                    onnxConfig, impactToken);
            return R.ok(createModelDraft(model, "UPDATE",
                    changeLog == null || changeLog.isBlank()
                            ? "替换模型文件" : changeLog));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/versions/{modelId}")
    public R<List<RuleModelVersion>> listVersions(@PathVariable Long modelId) {
        return R.ok(modelService.listVersions(modelId));
    }

    @GetMapping("/version/{modelId}/{version}")
    public R<RuleModelVersion> getVersion(@PathVariable Long modelId, @PathVariable Integer version) {
        RuleModelVersion snapshot = modelService.getVersion(modelId, version);
        return snapshot == null ? R.fail("Version not found") : R.ok(snapshot);
    }

    @GetMapping("/versionCompare/{modelId}")
    public R<Map<String, Object>> compareVersions(@PathVariable Long modelId,
            @RequestParam Integer leftVersion,
            @RequestParam Integer rightVersion) {
        try {
            return R.ok(modelService.compareVersions(modelId, leftVersion, rightVersion));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/rollback/{modelId}/{version}")
    @RequirePermission("model:edit")
    public R<GovernanceRequestView> rollback(@PathVariable Long modelId,
                                             @PathVariable Integer version,
                                             @RequestParam(defaultValue = "NEW") String restoreMode) {
        try {
            return R.ok(GovernanceRequestView.from(
                    legacyRestoreService.restoreModel(modelId, version, restoreMode,
                            operatorResolver.resolve())));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 查询项目下所有模型（非分页，设计器使用）
     */
    @GetMapping("/project/{projectId}/all")
    public R<List<RuleModel>> listAllByProject(@PathVariable Long projectId) {
        return R.ok(modelService.listByProject(projectId));
    }

    /**
     * 执行模型测试
     * @param id 模型ID
     * @param requestJson 原始输入参数，保留大整数和十进制精度
     */
    @PostMapping("/execute/{id}")
    public R<Map<String, Object>> execute(@PathVariable Long id, @RequestBody String requestJson,
                                          @RequestParam(required = false) Integer version) {
        long start = System.currentTimeMillis();
        RuleModel model = version == null ? modelService.getDetail(id) : new RuleModel();
        if (version != null) model.setId(id);
        Map<String, Object> params = Map.of();
        try {
            params = new com.hengshucredit.rule.server.service.RuleTestExpectationService().readJsonObject(requestJson, "模型测试参数");
            Map<String, Object> result = version == null ? modelService.execute(id, params)
                    : modelService.executeVersion(id, version, params);
            if (version != null) {
                model.setModelCode((String) result.get("modelCode"));
                model.setModelName((String) result.get("modelName"));
                model.setProjectCode((String) result.get("projectCode"));
                if (result.get("projectId") != null) model.setProjectId(Long.valueOf(String.valueOf(result.get("projectId"))));
            }
            logModelExecute(model, modelTestLogRequest(id, version, params), result, null, System.currentTimeMillis() - start);
            return R.ok(result);
        } catch (IllegalArgumentException e) {
            logModelExecute(model, modelTestLogRequest(id, version, params), null, e.getMessage(), System.currentTimeMillis() - start);
            return R.fail(e.getMessage());
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg == null || msg.isEmpty()) msg = e.toString();
            logModelExecute(model, modelTestLogRequest(id, version, params), null, "模型执行失败: " + msg, System.currentTimeMillis() - start);
            return R.fail("模型执行失败: " + msg);
        }
    }

    private Map<String, Object> modelTestLogRequest(Long id, Integer version, Map<String, Object> params) {
        return version == null ? params : Map.of("modelId", String.valueOf(id), "version", version, "params", params);
    }

    @GetMapping("/version/{id}/{version}/test-context")
    public R<Map<String, Object>> versionTestContext(@PathVariable Long id, @PathVariable Integer version) {
        try { return R.ok(modelService.versionTestContext(id, version)); }
        catch (IllegalArgumentException error) { return R.fail(error.getMessage()); }
    }

    @PostMapping("/version/{id}/{version}/testParams")
    @RequirePermission("model:edit")
    @org.springframework.transaction.annotation.Transactional
    public R<GovernanceRequestView> saveVersionTestParams(@PathVariable Long id, @PathVariable Integer version,
                                                         @RequestBody Map<String, String> body) {
        RuleModel draft = modelService.prepareVersionSampleDraft(id, version, body.get("testParams"));
        return R.ok(createModelDraft(draft, "UPDATE", "修改模型 v" + version + " 的测试参数"));
    }

    /**
     * 保存模型的测试参数（JSON）
     * @param id 模型ID
     * @param testParams JSON字符串的测试参数
     */
    @PostMapping("/testParams/{id}")
    @GovernedProjectionMutation
    public R<Void> saveTestParams(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            modelService.saveTestParams(id, body.get("testParams"));
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取模型的测试参数（JSON）
     * @param id 模型ID
     */
    @GetMapping("/testParams/{id}")
    public R<String> getTestParams(@PathVariable Long id) {
        String params = modelService.getTestParams(id);
        return R.ok(params);
    }

    /**
     * 更新模型输入字段（关联变量映射）
     */
    @PutMapping("/inputField/{id}")
    @GovernedProjectionMutation
    public R<Void> updateInputField(@PathVariable Long id, @RequestBody RuleModelInputField field) {
        try {
            modelService.updateInputField(id, field);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 更新模型输出字段（关联变量映射）
     */
    @PutMapping("/outputField/{id}")
    @GovernedProjectionMutation
    public R<Void> updateOutputField(@PathVariable Long id, @RequestBody RuleModelOutputField field) {
        try {
            modelService.updateOutputField(id, field);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 将项目级模型转为全局模型
     * @param id 模型ID
     * @param newModelCode 新的全局编码（用户重新填写）
     */
    @PostMapping("/toGlobal/{id:\\d+}")
    @RequirePermission("model:edit")
    public R<GovernanceRequestView> toGlobal(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            RuleModel model = modelService.getDetail(id);
            if (model == null) {
                return R.fail("模型不存在");
            }
            model.setModelCode(body.get("modelCode"));
            model.setScope("GLOBAL");
            model.setProjectId(0L);
            model.setProjectCode(null);
            model.setProjectName(null);
            return R.ok(createModelDraft(
                    model, "UPDATE", "将模型转为全局资源"));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    private void logModelExecute(RuleModel model, Map<String, Object> params, Map<String, Object> result,
                                 String errorMessage, long costTimeMs) {
        if (runtimeCallLogService == null) {
            return;
        }
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        log.setModuleType("MODEL");
        log.setActionType("EXECUTE");
        if (model != null) {
            log.setProjectId(model.getProjectId());
            log.setProjectCode(model.getProjectCode());
            log.setTargetRefId(model.getId());
            log.setTargetCode(model.getModelCode());
            log.setTargetName(model.getModelName());
        }
        log.setSuccess(errorMessage == null ? 1 : 0);
        log.setRequestBody(runtimeCallLogService.toJson(params));
        log.setResponseBody(runtimeCallLogService.toJson(result));
        log.setErrorMessage(errorMessage);
        log.setCostTimeMs(costTimeMs);
        runtimeCallLogService.safeSave(log);
    }

    private GovernanceRequestView createModelDraft(
            RuleModel model, String action, String changeSummary) {
        GovernanceDraftRequest request = new GovernanceDraftRequest();
        request.setResourceType("MODEL");
        request.setResourceId("CREATE".equals(action)
                ? null : model.getId());
        request.setProjectId(model.getProjectId());
        request.setAction(action);
        request.setSnapshotJson(JSON.toJSONString(model));
        request.setChangeSummary(changeSummary);
        return GovernanceRequestView.from(
                governanceApprovalService.createDraft(
                        request, operatorResolver.resolve()));
    }
}
