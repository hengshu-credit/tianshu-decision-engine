package com.hengshucredit.rule.server.controller;

import com.hengshucredit.rule.model.ApiResponse;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.core.engine.ExecutionCapacityException;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleModelVersion;
import com.hengshucredit.rule.model.entity.ResourceImpactAnalysis;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
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
    public ApiResponse<String> health() {
        return ApiResponse.ok("ok");
    }

    /**
     * 查询 ONNX Runtime execution provider 能力，供模型配置页面展示。
     */
    @GetMapping("/runtimeCapabilities")
    public ApiResponse<Map<String, Object>> runtimeCapabilities() {
        return ApiResponse.ok(modelService.runtimeCapabilities());
    }

    /**
     * 检查模型编码是否与现有模型冲突
     * @param modelCode 模型编码
     * @param scope GLOBAL / PROJECT
     * @param projectId 项目ID（仅 PROJECT 时需要）
     * @param excludeId 排除的模型ID（编辑时传自己的ID，跳过自身比对）
     */
    @GetMapping("/checkCode")
    public ApiResponse<Boolean> checkCode(
            @RequestParam String modelCode,
            @RequestParam String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long excludeId) {
        boolean exists = modelService.existsModelCodeConflict(modelCode, scope, projectId, excludeId);
        return ApiResponse.ok(exists);
    }

    /**
     * 上传并解析模型文件
     */
    @PostMapping("/upload")
    @RequirePermission("model:edit")
    public ApiResponse<GovernanceRequestView> upload(
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
            return ApiResponse.ok(createModelDraft(model, "CREATE",
                    changeLog == null || changeLog.isBlank()
                            ? "上传并新建模型" : changeLog));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        } catch (RuntimeException e) {
            return ApiResponse.fail(e);
        }
    }

    /**
     * 分页查询模型列表
     */
    @GetMapping("/list")
    public ApiResponse<IPage<RuleModel>> list(
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
        return ApiResponse.ok(modelService.pageList(pageNum, pageSize, projectId, scope,
                modelType, modelFormat, modelCode, modelName, projectCode, projectName));
    }

    /**
     * 获取模型详情（含字段信息）
     */
    @GetMapping("/{id}")
    public ApiResponse<RuleModel> get(@PathVariable Long id) {
        RuleModel model = modelService.getDetail(id);
        return model != null ? ApiResponse.ok(model) : ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "模型不存在");
    }

    /**
     * 更新模型元信息（不包括文件内容）
     */
    @PutMapping
    @GovernedProjectionMutation
    public ApiResponse<Void> update(@RequestBody RuleModel model) {
        try {
            modelService.update(model);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    /**
     * 删除模型
     */
    @DeleteMapping("/{id}")
    @GovernedProjectionMutation
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam String impactToken) {
        try {
            modelService.delete(id, impactToken);
            return ApiResponse.ok();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(e);
        }
    }

    /**
     * 发布模型
     */
    @PostMapping("/publish/{id}")
    @GovernedProjectionMutation
    public ApiResponse<Void> publish(@PathVariable Long id,
            @RequestParam(required = false) String changeLog) {
        try {
            modelService.publish(id, changeLog, null);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    /**
     * 下线模型
     */
    @PostMapping("/unpublish/{id}")
    @GovernedProjectionMutation
    public ApiResponse<Void> unpublish(@PathVariable Long id, @RequestParam String impactToken) {
        try {
            modelService.unpublish(id, impactToken);
            return ApiResponse.ok();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(e);
        }
    }

    @PostMapping("/impact/{id}")
    public ApiResponse<ResourceImpactAnalysis> analyzeImpact(@PathVariable Long id,
                                                   @RequestParam String action) {
        try {
            return ApiResponse.ok(impactAnalysisService.analyze("MODEL", id, action, null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(e);
        }
    }

    @PostMapping("/replace/{id}")
    @RequirePermission("model:edit")
    public ApiResponse<GovernanceRequestView> replace(@PathVariable Long id,
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
            return ApiResponse.ok(createModelDraft(model, "UPDATE",
                    changeLog == null || changeLog.isBlank()
                            ? "替换模型文件" : changeLog));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(e);
        }
    }

    @GetMapping("/versions/{modelId}")
    public ApiResponse<List<RuleModelVersion>> listVersions(@PathVariable Long modelId) {
        return ApiResponse.ok(modelService.listVersions(modelId));
    }

    @GetMapping("/version/{modelId}/{version}")
    public ApiResponse<RuleModelVersion> getVersion(@PathVariable Long modelId, @PathVariable Integer version) {
        RuleModelVersion snapshot = modelService.getVersion(modelId, version);
        return snapshot == null ? ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "Version not found") : ApiResponse.ok(snapshot);
    }

    @GetMapping("/versionCompare/{modelId}")
    public ApiResponse<Map<String, Object>> compareVersions(@PathVariable Long modelId,
            @RequestParam Integer leftVersion,
            @RequestParam Integer rightVersion) {
        try {
            return ApiResponse.ok(modelService.compareVersions(modelId, leftVersion, rightVersion));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    @PostMapping("/rollback/{modelId}/{version}")
    @RequirePermission("model:edit")
    public ApiResponse<GovernanceRequestView> rollback(@PathVariable Long modelId,
                                             @PathVariable Integer version,
                                             @RequestParam(defaultValue = "NEW") String restoreMode) {
        try {
            return ApiResponse.ok(GovernanceRequestView.from(
                    legacyRestoreService.restoreModel(modelId, version, restoreMode,
                            operatorResolver.resolve())));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    /**
     * 查询项目下所有模型（非分页，设计器使用）
     */
    @GetMapping("/project/{projectId}/all")
    public ApiResponse<List<RuleModel>> listAllByProject(@PathVariable Long projectId) {
        return ApiResponse.ok(modelService.listByProject(projectId));
    }

    /**
     * 执行模型测试
     * @param id 模型ID
     * @param requestJson 原始输入参数，保留大整数和十进制精度
     */
    @PostMapping("/execute/{id}")
    public ApiResponse<Map<String, Object>> execute(@PathVariable Long id, @RequestBody String requestJson,
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
            if (!modelExecutionSucceeded(result)) {
                String error = modelExecutionError(result);
                logModelExecute(model, modelTestLogRequest(id, version, params), result,
                        error, System.currentTimeMillis() - start);
                com.hengshucredit.rule.model.PlatformCode code = "THROTTLED".equals(result.get("executionStatus"))
                        ? com.hengshucredit.rule.model.PlatformCode.CAPACITY_EXCEEDED
                        : com.hengshucredit.rule.model.PlatformCode.MODEL_EXECUTION_ERROR;
                return ApiResponse.fail(code, error);
            }
            logModelExecute(model, modelTestLogRequest(id, version, params), result, null, System.currentTimeMillis() - start);
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            logModelExecute(model, modelTestLogRequest(id, version, params), null, e.getMessage(), System.currentTimeMillis() - start);
            return ApiResponse.fail(e);
        } catch (RuntimeException e) {
            ExecutionCapacityException capacity = ExecutionCapacityException.find(e);
            if (capacity != null) {
                logModelExecute(model, modelTestLogRequest(id, version, params), null,
                        capacity.getMessage(), System.currentTimeMillis() - start);
                return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.CAPACITY_EXCEEDED, capacity.getMessage());
            }
            String msg = e.getMessage();
            if (msg == null || msg.isEmpty()) msg = e.toString();
            logModelExecute(model, modelTestLogRequest(id, version, params), null, "模型执行失败: " + msg, System.currentTimeMillis() - start);
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.MODEL_EXECUTION_ERROR, msg);
        }
    }

    private boolean modelExecutionSucceeded(Map<String, Object> result) {
        Object value = result == null ? null : result.get("success");
        return Boolean.TRUE.equals(value)
                || value instanceof Number && ((Number) value).intValue() == 1;
    }

    private String modelExecutionError(Map<String, Object> result) {
        if (result == null) return "模型执行返回为空";
        Object value = result.get("error");
        if (value == null) value = result.get("message");
        return value == null ? "模型执行失败" : String.valueOf(value);
    }

    private Map<String, Object> modelTestLogRequest(Long id, Integer version, Map<String, Object> params) {
        return version == null ? params : Map.of("modelId", String.valueOf(id), "version", version, "params", params);
    }

    @GetMapping("/version/{id}/{version}/test-context")
    public ApiResponse<Map<String, Object>> versionTestContext(@PathVariable Long id, @PathVariable Integer version) {
        try { return ApiResponse.ok(modelService.versionTestContext(id, version)); }
        catch (IllegalArgumentException error) { return ApiResponse.fail(error); }
    }

    @PostMapping("/version/{id}/{version}/testParams")
    @RequirePermission("model:edit")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<GovernanceRequestView> saveVersionTestParams(@PathVariable Long id, @PathVariable Integer version,
                                                         @RequestBody Map<String, String> body) {
        RuleModel draft = modelService.prepareVersionSampleDraft(id, version, body.get("testParams"));
        return ApiResponse.ok(createModelDraft(draft, "UPDATE", "修改模型 v" + version + " 的测试参数"));
    }

    /**
     * 保存模型的测试参数（JSON）
     * @param id 模型ID
     * @param testParams JSON字符串的测试参数
     */
    @PostMapping("/testParams/{id}")
    @GovernedProjectionMutation
    public ApiResponse<Void> saveTestParams(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            modelService.saveTestParams(id, body.get("testParams"));
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    /**
     * 获取模型的测试参数（JSON）
     * @param id 模型ID
     */
    @GetMapping("/testParams/{id}")
    public ApiResponse<String> getTestParams(@PathVariable Long id) {
        String params = modelService.getTestParams(id);
        return ApiResponse.ok(params);
    }

    /**
     * 更新模型输入字段（关联变量映射）
     */
    @PutMapping("/inputField/{id}")
    @GovernedProjectionMutation
    public ApiResponse<Void> updateInputField(@PathVariable Long id, @RequestBody RuleModelInputField field) {
        try {
            modelService.updateInputField(id, field);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    /**
     * 更新模型输出字段（关联变量映射）
     */
    @PutMapping("/outputField/{id}")
    @GovernedProjectionMutation
    public ApiResponse<Void> updateOutputField(@PathVariable Long id, @RequestBody RuleModelOutputField field) {
        try {
            modelService.updateOutputField(id, field);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    /**
     * 将项目级模型转为全局模型
     * @param id 模型ID
     * @param newModelCode 新的全局编码（用户重新填写）
     */
    @PostMapping("/toGlobal/{id:\\d+}")
    @RequirePermission("model:edit")
    public ApiResponse<GovernanceRequestView> toGlobal(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            RuleModel model = modelService.getDetail(id);
            if (model == null) {
                return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "模型不存在");
            }
            model.setModelCode(body.get("modelCode"));
            model.setScope("GLOBAL");
            model.setProjectId(0L);
            model.setProjectCode(null);
            model.setProjectName(null);
            return ApiResponse.ok(createModelDraft(
                    model, "UPDATE", "将模型转为全局资源"));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
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
            Object version = result == null ? null : result.get("version");
            log.setModelVersion(version == null ? model.getCurrentVersion()
                    : Integer.valueOf(String.valueOf(version)));
            log.setModelDigest(result == null ? null : stringValue(result.get("modelDigest")));
            log.setConfigurationDigest(result == null ? null : stringValue(result.get("configurationDigest")));
            log.setConfiguredProvider(result == null ? null : stringValue(result.get("configuredProvider")));
            log.setEffectiveProvider(result == null ? null : stringValue(result.get("effectiveProvider")));
            log.setExecutionStatus(result == null ? null : stringValue(result.get("executionStatus")));
        }
        log.setSuccess(errorMessage == null ? 1 : 0);
        log.setRequestBody(runtimeCallLogService.toJson(params));
        log.setResponseBody(runtimeCallLogService.toJson(result));
        log.setErrorMessage(errorMessage);
        log.setCostTimeMs(costTimeMs);
        runtimeCallLogService.safeSave(log);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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
