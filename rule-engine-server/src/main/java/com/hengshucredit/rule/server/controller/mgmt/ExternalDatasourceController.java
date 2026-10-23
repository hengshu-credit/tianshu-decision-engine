package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.governance.GovernedProjectionMutation;
import com.hengshucredit.rule.server.service.ExternalApiInvokeService;
import com.hengshucredit.rule.server.service.RuleExternalApiConfigService;
import com.hengshucredit.rule.server.service.RuleExternalDatasourceService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/datasource")
public class ExternalDatasourceController {

    @Resource private com.hengshucredit.rule.server.service.ExternalApiTrialService trialService;
    @Resource private com.hengshucredit.rule.server.service.ConsoleOperatorResolver operatorResolver;
    @Resource private com.hengshucredit.rule.server.governance.VariableSourceReferenceValidator sourceReferenceValidator;

    @PostMapping("/api-config/trials")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> createTrial(@RequestBody Map<String, Object> body) {
        if (!(body.get("params") instanceof Map<?, ?>)) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, "试运行参数必须是 JSON 对象");
        if (!(body.get("config") instanceof Map<?, ?>)) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, "API接口配置必须是 JSON 对象");
        try {
            RuleExternalApiConfig config = JSON.parseObject(JSON.toJSONString(body.get("config")), RuleExternalApiConfig.class);
            return ApiResponse.ok(trialService.create(operatorResolver.resolve(), (String) body.get("requestKey"),
                    config, (Map<String, Object>) body.get("params")));
        } catch (IllegalArgumentException error) { return ApiResponse.fail(error); }
    }

    @GetMapping("/api-config/trials")
    public ApiResponse<java.util.List<Map<String, Object>>> listTrials() {
        return ApiResponse.ok(trialService.list(operatorResolver.resolve()));
    }

    @GetMapping("/api-config/trials/{trialId}")
    public ApiResponse<Map<String, Object>> getTrial(@PathVariable String trialId) {
        try { return ApiResponse.ok(trialService.get(operatorResolver.resolve(), trialId)); }
        catch (IllegalArgumentException error) { return ApiResponse.fail(error); }
    }

    @PostMapping("/api-config/trials/{trialId}/resume")
    public ApiResponse<Map<String, Object>> resumeTrial(@PathVariable String trialId) {
        try { return ApiResponse.ok(trialService.resume(operatorResolver.resolve(), trialId)); }
        catch (IllegalArgumentException error) { return ApiResponse.fail(error); }
    }

    @PostMapping("/api-config/trials/{trialId}/cancel")
    public ApiResponse<Map<String, Object>> cancelTrial(@PathVariable String trialId) {
        try { return ApiResponse.ok(trialService.cancel(operatorResolver.resolve(), trialId)); }
        catch (IllegalArgumentException error) { return ApiResponse.fail(error); }
    }

    @Resource
    private RuleExternalDatasourceService datasourceService;

    @Resource
    private RuleExternalApiConfigService apiConfigService;

    @Resource
    private ExternalApiInvokeService externalApiInvokeService;

    @GetMapping("/list")
    public ApiResponse<IPage<RuleExternalDatasource>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String datasourceCode,
            @RequestParam(required = false) String datasourceName,
            @RequestParam(required = false) String authType,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(datasourceService.pageList(pageNum, pageSize, scope, projectId, projectCode, projectName,
                datasourceCode, datasourceName, authType, status));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<RuleExternalDatasource> get(@PathVariable Long id) {
        return ApiResponse.ok(datasourceService.getById(id));
    }

    @PostMapping
    @GovernedProjectionMutation
    public ApiResponse<Void> create(@RequestBody RuleExternalDatasource datasource) {
        datasourceService.saveWithDefaults(datasource);
        return ApiResponse.ok();
    }

    @PutMapping
    @GovernedProjectionMutation
    public ApiResponse<Void> update(@RequestBody RuleExternalDatasource datasource) {
        datasourceService.updateWithDefaults(datasource);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    @GovernedProjectionMutation
    public ApiResponse<Void> delete(@PathVariable Long id) {
        apiConfigService.deleteByDatasourceId(id);
        datasourceService.removeById(id);
        return ApiResponse.ok();
    }

    @GetMapping("/api-config/list")
    public ApiResponse<IPage<RuleExternalApiConfig>> listApiConfigs(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String datasourceCode,
            @RequestParam(required = false) String apiCode,
            @RequestParam(required = false) String apiName,
            @RequestParam(required = false) String requestMode,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(apiConfigService.pageList(pageNum, pageSize, datasourceId, projectId, projectCode, projectName,
                datasourceCode, apiCode, apiName, requestMode, status));
    }

    @GetMapping("/api-config/available")
    public ApiResponse<java.util.List<Map<String, Object>>> availableApiConfigs(@RequestParam Long projectId) {
        return ApiResponse.ok(sourceReferenceValidator.apiReferenceCatalog(projectId));
    }

    @GetMapping("/api-config/{id:\\d+}")
    public ApiResponse<RuleExternalApiConfig> getApiConfig(@PathVariable Long id) {
        return ApiResponse.ok(apiConfigService.getById(id));
    }

    @GetMapping("/api-config/{id:\\d+}/binding-contract")
    public ApiResponse<Map<String, Object>> bindingContract(@PathVariable Long id) {
        RuleExternalApiConfig api = apiConfigService.getById(id);
        if (api == null || !Integer.valueOf(1).equals(api.getStatus())) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "外数 API 不存在或未启用");
        var specification = com.hengshucredit.rule.server.service.ExternalApiRequestPlan.specification(api);
        if (specification == null) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, "外数 API 必须使用 V2 executionConfig");
        var fields = new java.util.ArrayList<>(com.hengshucredit.rule.server.service.ExternalApiRequestPlan.fields(specification));
        if (specification != null && specification.getJSONArray("requestBranches") != null) {
            for (Object raw : specification.getJSONArray("requestBranches")) fields.addAll(
                    com.hengshucredit.rule.server.service.ExternalApiRequestPlan.fields(JSON.parseObject(JSON.toJSONString(raw))));
        }
        fields.forEach(field -> { field.put("overridable", field.getBooleanValue("overridable")); if ("HEADER".equals(field.getString("location"))) { field.remove("value"); field.remove("defaultValue"); field.put("overridable", false); } });
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("apiConfigId", String.valueOf(id)); result.put("apiName", api.getApiName()); result.put("apiCode", api.getApiCode());
        result.put("bindingMode", "FIELDS");
        result.put("requestFields", fields);
        result.put("resultFields", com.hengshucredit.rule.server.service.ExternalApiResponseSchema.catalog(api));
        result.put("steps", specification == null || specification.getJSONArray("steps") == null ? java.util.List.of()
                : specification.getJSONArray("steps").stream().map(raw -> {
                    var step = JSON.parseObject(JSON.toJSONString(raw));
                    Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                    for (String key : java.util.List.of("id", "name", "type")) metadata.put(key, step.get(key));
                    return metadata;
                }).toList());
        return ApiResponse.ok(result);
    }

    @PostMapping("/api-config/{id:\\d+}/response-preview")
    public ApiResponse<Map<String, Object>> previewResponse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            RuleExternalApiConfig api = JSON.parseObject(JSON.toJSONString(body.get("config")), RuleExternalApiConfig.class);
            api.setId(id);
            Map<String, Object> sample = JSON.parseObject(JSON.toJSONString(body.get("sample")));
            return ApiResponse.ok(externalApiInvokeService.previewResponse(api, sample == null ? Map.of() : sample));
        } catch (IllegalArgumentException e) { return ApiResponse.fail(e); }
    }

    @PostMapping("/api-config")
    @GovernedProjectionMutation
    public ApiResponse<RuleExternalApiConfig> createApiConfig(@RequestBody RuleExternalApiConfig config) {
        return ApiResponse.ok(apiConfigService.saveWithDefaults(config));
    }

    @PutMapping("/api-config")
    @GovernedProjectionMutation
    public ApiResponse<RuleExternalApiConfig> updateApiConfig(@RequestBody RuleExternalApiConfig config) {
        return ApiResponse.ok(apiConfigService.updateWithDefaults(config));
    }

    @DeleteMapping("/api-config/{id:\\d+}")
    @GovernedProjectionMutation
    public ApiResponse<Void> deleteApiConfig(@PathVariable Long id) {
        apiConfigService.removeApiConfig(id);
        return ApiResponse.ok();
    }

    @PostMapping("/api-config/{id:\\d+}/invoke")
    public ApiResponse<Map<String, Object>> invokeApiConfig(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> params) {
        try {
            return invocationResult(externalApiInvokeService.invoke(id, params));
        } catch (ExternalApiInvokeService.BillingPersistenceException e) {
            return billingFailure(e);
        } catch (Exception e) {
            return invocationFailure(e);
        }
    }

    @PostMapping("/api-config/{id:\\d+}/invoke-preview")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> invokeApiConfigPreview(@PathVariable Long id,
                                                         @RequestBody Map<String, Object> body) {
        try {
            RuleExternalApiConfig config = JSON.parseObject(
                    JSON.toJSONString(body.get("config")), RuleExternalApiConfig.class);
            if (config == null) {
                return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUIRED_FIELD_MISSING, "API接口配置不能为空");
            }
            config.setId(id);
            Map<String, Object> params = body.get("params") instanceof Map
                    ? (Map<String, Object>) body.get("params") : Collections.emptyMap();
            return invocationResult(externalApiInvokeService.invoke(config, params));
        } catch (ExternalApiInvokeService.BillingPersistenceException e) {
            return billingFailure(e);
        } catch (Exception e) {
            return invocationFailure(e);
        }
    }

    private ApiResponse<Map<String, Object>> invocationResult(Map<String, Object> data) {
        return Boolean.FALSE.equals(data.get("success"))
                ? ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.EXTERNAL_API_ERROR,
                        (String) data.get("errorMessage"), data) : ApiResponse.ok(data);
    }

    private ApiResponse<Map<String, Object>> billingFailure(ExternalApiInvokeService.BillingPersistenceException error) {
        ApiResponse<Map<String, Object>> result = ApiResponse.fail(error);
        result.setData(error.getObservedResponse());
        return result;
    }

    private ApiResponse<Map<String, Object>> invocationFailure(Exception error) {
        var code = com.hengshucredit.rule.model.PlatformErrors.classify(error);
        if (code == com.hengshucredit.rule.model.PlatformCode.SYSTEM_ERROR) {
            code = com.hengshucredit.rule.model.PlatformCode.EXTERNAL_API_ERROR;
        }
        return ApiResponse.fail(code, com.hengshucredit.rule.model.PlatformErrors.detail(error));
    }

    @PostMapping("/api-config/{id:\\d+}/request-preview")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> previewApiConfigRequest(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body) {
        try {
            RuleExternalApiConfig config = JSON.parseObject(
                    JSON.toJSONString(body.get("config")), RuleExternalApiConfig.class);
            if (config == null) {
                return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUIRED_FIELD_MISSING, "API接口配置不能为空");
            }
            config.setId(id);
            Map<String, Object> params = body.get("params") instanceof Map
                    ? (Map<String, Object>) body.get("params") : Collections.emptyMap();
            String previewToken = body.get("previewToken") == null
                    ? "" : String.valueOf(body.get("previewToken"));
            return ApiResponse.ok(externalApiInvokeService.previewRequest(config, params, previewToken));
        } catch (Exception e) {
            return ApiResponse.fail(e);
        }
    }

    @PostMapping("/{id:\\d+}/auth-test")
    public ApiResponse<Map<String, Object>> testDatasourceAuth(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> params) {
        try {
            return ApiResponse.ok(externalApiInvokeService.testDatasourceAuth(id, params));
        } catch (Exception e) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.EXTERNAL_API_ERROR, e.getMessage());
        }
    }
}
