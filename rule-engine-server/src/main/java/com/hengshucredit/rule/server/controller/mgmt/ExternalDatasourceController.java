package com.hengshucredit.rule.server.controller.mgmt;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.common.R;
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

    @PostMapping("/api-config/trials")
    @SuppressWarnings("unchecked")
    public R<Map<String, Object>> createTrial(@RequestBody Map<String, Object> body) {
        if (!(body.get("params") instanceof Map<?, ?>)) return R.fail("试运行参数必须是 JSON 对象");
        if (!(body.get("config") instanceof Map<?, ?>)) return R.fail("API接口配置必须是 JSON 对象");
        try {
            RuleExternalApiConfig config = JSON.parseObject(JSON.toJSONString(body.get("config")), RuleExternalApiConfig.class);
            return R.ok(trialService.create(operatorResolver.resolve(), (String) body.get("requestKey"),
                    config, (Map<String, Object>) body.get("params")));
        } catch (IllegalArgumentException error) { return R.fail(error.getMessage()); }
    }

    @GetMapping("/api-config/trials")
    public R<java.util.List<Map<String, Object>>> listTrials() {
        return R.ok(trialService.list(operatorResolver.resolve()));
    }

    @GetMapping("/api-config/trials/{trialId}")
    public R<Map<String, Object>> getTrial(@PathVariable String trialId) {
        try { return R.ok(trialService.get(operatorResolver.resolve(), trialId)); }
        catch (IllegalArgumentException error) { return R.fail(error.getMessage()); }
    }

    @PostMapping("/api-config/trials/{trialId}/resume")
    public R<Map<String, Object>> resumeTrial(@PathVariable String trialId) {
        try { return R.ok(trialService.resume(operatorResolver.resolve(), trialId)); }
        catch (IllegalArgumentException error) { return R.fail(error.getMessage()); }
    }

    @PostMapping("/api-config/trials/{trialId}/cancel")
    public R<Map<String, Object>> cancelTrial(@PathVariable String trialId) {
        try { return R.ok(trialService.cancel(operatorResolver.resolve(), trialId)); }
        catch (IllegalArgumentException error) { return R.fail(error.getMessage()); }
    }

    @Resource
    private RuleExternalDatasourceService datasourceService;

    @Resource
    private RuleExternalApiConfigService apiConfigService;

    @Resource
    private ExternalApiInvokeService externalApiInvokeService;

    @GetMapping("/list")
    public R<IPage<RuleExternalDatasource>> list(
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
        return R.ok(datasourceService.pageList(pageNum, pageSize, scope, projectId, projectCode, projectName,
                datasourceCode, datasourceName, authType, status));
    }

    @GetMapping("/{id:\\d+}")
    public R<RuleExternalDatasource> get(@PathVariable Long id) {
        return R.ok(datasourceService.getById(id));
    }

    @PostMapping
    @GovernedProjectionMutation
    public R<Void> create(@RequestBody RuleExternalDatasource datasource) {
        datasourceService.saveWithDefaults(datasource);
        return R.ok();
    }

    @PutMapping
    @GovernedProjectionMutation
    public R<Void> update(@RequestBody RuleExternalDatasource datasource) {
        datasourceService.updateWithDefaults(datasource);
        return R.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    @GovernedProjectionMutation
    public R<Void> delete(@PathVariable Long id) {
        apiConfigService.deleteByDatasourceId(id);
        datasourceService.removeById(id);
        return R.ok();
    }

    @GetMapping("/api-config/list")
    public R<IPage<RuleExternalApiConfig>> listApiConfigs(
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
        return R.ok(apiConfigService.pageList(pageNum, pageSize, datasourceId, projectId, projectCode, projectName,
                datasourceCode, apiCode, apiName, requestMode, status));
    }

    @GetMapping("/api-config/{id:\\d+}")
    public R<RuleExternalApiConfig> getApiConfig(@PathVariable Long id) {
        return R.ok(apiConfigService.getById(id));
    }

    @GetMapping("/api-config/{id:\\d+}/binding-contract")
    public R<Map<String, Object>> bindingContract(@PathVariable Long id) {
        RuleExternalApiConfig api = apiConfigService.getById(id);
        if (api == null || !Integer.valueOf(1).equals(api.getStatus())) return R.fail("外数 API 不存在或未启用");
        var specification = com.hengshucredit.rule.server.service.ExternalApiRequestPlan.specification(api);
        var fields = new java.util.ArrayList<>(com.hengshucredit.rule.server.service.ExternalApiRequestPlan.fields(specification));
        if (specification != null && specification.getJSONArray("requestBranches") != null) {
            for (Object raw : specification.getJSONArray("requestBranches")) fields.addAll(
                    com.hengshucredit.rule.server.service.ExternalApiRequestPlan.fields(JSON.parseObject(JSON.toJSONString(raw))));
        }
        fields.forEach(field -> { field.put("overridable", field.getBooleanValue("overridable")); if ("HEADER".equals(field.getString("location"))) { field.remove("value"); field.remove("defaultValue"); field.put("overridable", false); } });
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("apiConfigId", String.valueOf(id)); result.put("apiName", api.getApiName()); result.put("apiCode", api.getApiCode());
        result.put("bindingMode", specification == null ? "INPUT" : "FIELDS");
        result.put("requestFields", fields);
        result.put("inputFields", com.hengshucredit.rule.server.service.ExternalApiRequestPlan.legacyInputPaths(api).stream()
                .map(path -> Map.of("id", path, "path", path, "location", "INPUT", "overridable", true)).toList());
        result.put("resultFields", com.hengshucredit.rule.server.service.ExternalApiResponseSchema.catalog(api));
        result.put("steps", specification == null || specification.getJSONArray("steps") == null ? java.util.List.of()
                : specification.getJSONArray("steps").stream().map(raw -> {
                    var step = JSON.parseObject(JSON.toJSONString(raw));
                    Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                    for (String key : java.util.List.of("id", "name", "type")) metadata.put(key, step.get(key));
                    return metadata;
                }).toList());
        return R.ok(result);
    }

    @PostMapping("/api-config/{id:\\d+}/response-preview")
    public R<Map<String, Object>> previewResponse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            RuleExternalApiConfig api = JSON.parseObject(JSON.toJSONString(body.get("config")), RuleExternalApiConfig.class);
            api.setId(id);
            Map<String, Object> sample = JSON.parseObject(JSON.toJSONString(body.get("sample")));
            return R.ok(externalApiInvokeService.previewResponse(api, sample == null ? Map.of() : sample));
        } catch (IllegalArgumentException e) { return R.fail(e.getMessage()); }
    }

    @PostMapping("/api-config")
    @GovernedProjectionMutation
    public R<RuleExternalApiConfig> createApiConfig(@RequestBody RuleExternalApiConfig config) {
        return R.ok(apiConfigService.saveWithDefaults(config));
    }

    @PutMapping("/api-config")
    @GovernedProjectionMutation
    public R<RuleExternalApiConfig> updateApiConfig(@RequestBody RuleExternalApiConfig config) {
        return R.ok(apiConfigService.updateWithDefaults(config));
    }

    @DeleteMapping("/api-config/{id:\\d+}")
    @GovernedProjectionMutation
    public R<Void> deleteApiConfig(@PathVariable Long id) {
        apiConfigService.removeApiConfig(id);
        return R.ok();
    }

    @PostMapping("/api-config/{id:\\d+}/invoke")
    public R<Map<String, Object>> invokeApiConfig(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> params) {
        try {
            return R.ok(externalApiInvokeService.invoke(id, params));
        } catch (ExternalApiInvokeService.BillingPersistenceException e) {
            return billingFailure(e);
        } catch (Exception e) {
            return R.fail("调用失败：" + e.getMessage());
        }
    }

    @PostMapping("/api-config/{id:\\d+}/invoke-preview")
    @SuppressWarnings("unchecked")
    public R<Map<String, Object>> invokeApiConfigPreview(@PathVariable Long id,
                                                         @RequestBody Map<String, Object> body) {
        try {
            RuleExternalApiConfig config = JSON.parseObject(
                    JSON.toJSONString(body.get("config")), RuleExternalApiConfig.class);
            if (config == null) {
                return R.fail("API接口配置不能为空");
            }
            config.setId(id);
            Map<String, Object> params = body.get("params") instanceof Map
                    ? (Map<String, Object>) body.get("params") : Collections.emptyMap();
            return R.ok(externalApiInvokeService.invoke(config, params));
        } catch (ExternalApiInvokeService.BillingPersistenceException e) {
            return billingFailure(e);
        } catch (Exception e) {
            return R.fail("调用失败：" + e.getMessage());
        }
    }

    private R<Map<String, Object>> billingFailure(ExternalApiInvokeService.BillingPersistenceException error) {
        R<Map<String, Object>> result = R.fail(error.getMessage());
        result.setData(error.getObservedResponse());
        return result;
    }

    @PostMapping("/api-config/{id:\\d+}/request-preview")
    @SuppressWarnings("unchecked")
    public R<Map<String, Object>> previewApiConfigRequest(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body) {
        try {
            RuleExternalApiConfig config = JSON.parseObject(
                    JSON.toJSONString(body.get("config")), RuleExternalApiConfig.class);
            if (config == null) {
                return R.fail("API接口配置不能为空");
            }
            config.setId(id);
            Map<String, Object> params = body.get("params") instanceof Map
                    ? (Map<String, Object>) body.get("params") : Collections.emptyMap();
            String previewToken = body.get("previewToken") == null
                    ? "" : String.valueOf(body.get("previewToken"));
            return R.ok(externalApiInvokeService.previewRequest(config, params, previewToken));
        } catch (Exception e) {
            return R.fail("请求预览失败：" + e.getMessage());
        }
    }

    @PostMapping("/{id:\\d+}/auth-test")
    public R<Map<String, Object>> testDatasourceAuth(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> params) {
        try {
            return R.ok(externalApiInvokeService.testDatasourceAuth(id, params));
        } catch (Exception e) {
            return R.fail("鉴权测试失败：" + e.getMessage());
        }
    }
}
