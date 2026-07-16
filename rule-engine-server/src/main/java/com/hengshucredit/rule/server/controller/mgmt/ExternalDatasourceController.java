package com.hengshucredit.rule.server.controller.mgmt;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.ExternalApiInvokeService;
import com.hengshucredit.rule.server.service.RuleExternalApiConfigService;
import com.hengshucredit.rule.server.service.RuleExternalDatasourceService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/datasource")
public class ExternalDatasourceController {

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
            @RequestParam(required = false) String datasourceCode,
            @RequestParam(required = false) String datasourceName,
            @RequestParam(required = false) String authType,
            @RequestParam(required = false) Integer status) {
        return R.ok(datasourceService.pageList(pageNum, pageSize, scope, projectId, datasourceCode, datasourceName, authType, status));
    }

    @GetMapping("/{id:\\d+}")
    public R<RuleExternalDatasource> get(@PathVariable Long id) {
        return R.ok(datasourceService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody RuleExternalDatasource datasource) {
        datasourceService.saveWithDefaults(datasource);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody RuleExternalDatasource datasource) {
        datasourceService.updateWithDefaults(datasource);
        return R.ok();
    }

    @DeleteMapping("/{id:\\d+}")
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
            @RequestParam(required = false) String datasourceCode,
            @RequestParam(required = false) String apiCode,
            @RequestParam(required = false) String apiName,
            @RequestParam(required = false) String requestMode,
            @RequestParam(required = false) Integer status) {
        return R.ok(apiConfigService.pageList(pageNum, pageSize, datasourceId, datasourceCode, apiCode, apiName, requestMode, status));
    }

    @GetMapping("/api-config/{id:\\d+}")
    public R<RuleExternalApiConfig> getApiConfig(@PathVariable Long id) {
        return R.ok(apiConfigService.getById(id));
    }

    @PostMapping("/api-config")
    public R<RuleExternalApiConfig> createApiConfig(@RequestBody RuleExternalApiConfig config) {
        return R.ok(apiConfigService.saveWithDefaults(config));
    }

    @PutMapping("/api-config")
    public R<RuleExternalApiConfig> updateApiConfig(@RequestBody RuleExternalApiConfig config) {
        return R.ok(apiConfigService.updateWithDefaults(config));
    }

    @DeleteMapping("/api-config/{id:\\d+}")
    public R<Void> deleteApiConfig(@PathVariable Long id) {
        apiConfigService.removeById(id);
        return R.ok();
    }

    @PostMapping("/api-config/{id:\\d+}/invoke")
    public R<Map<String, Object>> invokeApiConfig(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> params) {
        try {
            return R.ok(externalApiInvokeService.invoke(id, params));
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
        } catch (Exception e) {
            return R.fail("调用失败：" + e.getMessage());
        }
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
