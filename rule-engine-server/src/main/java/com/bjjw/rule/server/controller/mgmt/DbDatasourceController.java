package com.bjjw.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjjw.rule.model.entity.RuleDbDatasource;
import com.bjjw.rule.model.entity.RuleRuntimeCallLog;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.DBConnectPools;
import com.bjjw.rule.server.service.RuleDbDatasourceService;
import com.bjjw.rule.server.service.RuleRuntimeCallLogService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/database")
public class DbDatasourceController {

    @Resource
    private RuleDbDatasourceService datasourceService;

    @Resource
    private DBConnectPools dbConnectPools;

    @Resource
    private RuleRuntimeCallLogService runtimeCallLogService;

    @GetMapping("/list")
    public R<IPage<RuleDbDatasource>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String datasourceCode,
            @RequestParam(required = false) String datasourceName,
            @RequestParam(required = false) String dbType,
            @RequestParam(required = false) Integer status) {
        return R.ok(datasourceService.pageList(pageNum, pageSize, scope, projectId, datasourceCode, datasourceName, dbType, status));
    }

    @GetMapping("/{id:\\d+}")
    public R<RuleDbDatasource> get(@PathVariable Long id) {
        return R.ok(datasourceService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody RuleDbDatasource datasource) {
        datasourceService.saveWithDefaults(datasource);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody RuleDbDatasource datasource) {
        datasourceService.updateWithDefaults(datasource);
        return R.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        datasourceService.deleteDatasource(id);
        return R.ok();
    }

    @PostMapping("/{id:\\d+}/test")
    public R<String> testSavedConnection(@PathVariable Long id) {
        long start = System.currentTimeMillis();
        RuleDbDatasource datasource = datasourceService.getById(id);
        try {
            datasourceService.testConnection(id);
            logDatabaseCall(datasource, "TEST_CONNECTION", null, null, null, true, "连接成功", null, System.currentTimeMillis() - start);
            return R.ok("连接成功");
        } catch (Exception e) {
            logDatabaseCall(datasource, "TEST_CONNECTION", null, null, null, false, null, e.getMessage(), System.currentTimeMillis() - start);
            return R.fail("连接失败：" + e.getMessage());
        }
    }

    @PostMapping("/test")
    public R<String> testConnection(@RequestBody RuleDbDatasource datasource) {
        long start = System.currentTimeMillis();
        try {
            datasourceService.testConnection(datasource);
            logDatabaseCall(datasource, "TEST_CONNECTION_DRAFT", null, null, safeDatasourcePreview(datasource), true, "连接成功", null, System.currentTimeMillis() - start);
            return R.ok("连接成功");
        } catch (Exception e) {
            logDatabaseCall(datasource, "TEST_CONNECTION_DRAFT", null, null, safeDatasourcePreview(datasource), false, null, e.getMessage(), System.currentTimeMillis() - start);
            return R.fail("连接失败：" + e.getMessage());
        }
    }

    @PostMapping("/{id:\\d+}/query")
    public R<List<Map<String, Object>>> query(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        long start = System.currentTimeMillis();
        RuleDbDatasource datasource = datasourceService.getById(id);
        String sql = body.get("sql") == null ? null : String.valueOf(body.get("sql"));
        List<Object> params = body.get("params") instanceof List ? (List<Object>) body.get("params") : Collections.emptyList();
        Integer maxRows = body.get("maxRows") instanceof Number ? ((Number) body.get("maxRows")).intValue() : 100;
        try {
            List<Map<String, Object>> rows = dbConnectPools.query(id, sql, params, maxRows);
            logDatabaseCall(datasource, "QUERY", null, body, null, true, rows, null, System.currentTimeMillis() - start);
            return R.ok(rows);
        } catch (Exception e) {
            logDatabaseCall(datasource, "QUERY", null, body, null, false, null, e.getMessage(), System.currentTimeMillis() - start);
            return R.fail("查询失败：" + e.getMessage());
        }
    }

    private void logDatabaseCall(RuleDbDatasource datasource, String actionType, String requestUrl, Object requestParams,
                                 Object requestBody, boolean success, Object responseBody, String errorMessage, long costTimeMs) {
        if (runtimeCallLogService == null) {
            return;
        }
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        log.setModuleType("DATABASE");
        log.setActionType(actionType);
        if (datasource != null) {
            log.setProjectId(datasource.getProjectId());
            log.setTargetRefId(datasource.getId());
            log.setTargetCode(datasource.getDatasourceCode());
            log.setTargetName(datasource.getDatasourceName());
            log.setRequestUrl(requestUrl != null ? requestUrl : datasource.getJdbcUrl());
        } else {
            log.setRequestUrl(requestUrl);
        }
        log.setRequestParams(runtimeCallLogService.toJson(requestParams));
        log.setRequestBody(runtimeCallLogService.toJson(requestBody));
        log.setResponseBody(runtimeCallLogService.toJson(responseBody));
        log.setSuccess(success ? 1 : 0);
        log.setErrorMessage(errorMessage);
        log.setCostTimeMs(costTimeMs);
        runtimeCallLogService.safeSave(log);
    }

    private Map<String, Object> safeDatasourcePreview(RuleDbDatasource datasource) {
        if (datasource == null) {
            return Collections.emptyMap();
        }
        java.util.LinkedHashMap<String, Object> preview = new java.util.LinkedHashMap<>();
        preview.put("scope", datasource.getScope());
        preview.put("projectId", datasource.getProjectId());
        preview.put("datasourceCode", datasource.getDatasourceCode());
        preview.put("datasourceName", datasource.getDatasourceName());
        preview.put("dbType", datasource.getDbType());
        preview.put("connectionMode", datasource.getConnectionMode());
        preview.put("jdbcUrl", datasource.getJdbcUrl());
        preview.put("username", datasource.getUsername());
        preview.put("password", datasource.getPassword() == null || datasource.getPassword().isEmpty() ? "" : "******");
        return preview;
    }
}
