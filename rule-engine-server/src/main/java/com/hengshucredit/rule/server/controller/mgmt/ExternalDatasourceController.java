package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.ExternalApiInvokeService;
import com.hengshucredit.rule.server.service.RuleExternalApiConfigService;
import com.hengshucredit.rule.server.service.RuleExternalDatasourceService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
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
    public R<Void> createApiConfig(@RequestBody RuleExternalApiConfig config) {
        apiConfigService.saveWithDefaults(config);
        return R.ok();
    }

    @PutMapping("/api-config")
    public R<Void> updateApiConfig(@RequestBody RuleExternalApiConfig config) {
        apiConfigService.updateWithDefaults(config);
        return R.ok();
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

    @PostMapping("/{id:\\d+}/auth-test")
    public R<Map<String, Object>> testDatasourceAuth(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> params) {
        try {
            return R.ok(externalApiInvokeService.testDatasourceAuth(id, params));
        } catch (Exception e) {
            return R.fail("鉴权测试失败：" + e.getMessage());
        }
    }
}
