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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        LocalDateTime startTime = LocalDateTime.now();
        RuleDbDatasource datasource = datasourceService.getById(id);
        try {
            datasourceService.testConnection(id);
            logDatabaseCall(datasource, "TEST_CONNECTION", buildDatabaseRequest(datasource, null, null, null, null, startTime),
                    buildDatabaseResponse("SUCCESS", "连接成功", null, null, null, startTime), true, null, System.currentTimeMillis() - start);
            return R.ok("连接成功");
        } catch (Exception e) {
            logDatabaseCall(datasource, "TEST_CONNECTION", buildDatabaseRequest(datasource, null, null, null, null, startTime),
                    buildDatabaseResponse("FAILED", null, null, null, e.getMessage(), startTime), false, e.getMessage(), System.currentTimeMillis() - start);
            return R.fail("连接失败：" + e.getMessage());
        }
    }

    @PostMapping("/test")
    public R<String> testConnection(@RequestBody RuleDbDatasource datasource) {
        long start = System.currentTimeMillis();
        LocalDateTime startTime = LocalDateTime.now();
        try {
            datasourceService.testConnection(datasource);
            logDatabaseCall(datasource, "TEST_CONNECTION_DRAFT", buildDatabaseRequest(datasource, null, null, null, safeDatasourcePreview(datasource), startTime),
                    buildDatabaseResponse("SUCCESS", "连接成功", null, null, null, startTime), true, null, System.currentTimeMillis() - start);
            return R.ok("连接成功");
        } catch (Exception e) {
            logDatabaseCall(datasource, "TEST_CONNECTION_DRAFT", buildDatabaseRequest(datasource, null, null, null, safeDatasourcePreview(datasource), startTime),
                    buildDatabaseResponse("FAILED", null, null, null, e.getMessage(), startTime), false, e.getMessage(), System.currentTimeMillis() - start);
            return R.fail("连接失败：" + e.getMessage());
        }
    }

    @PostMapping("/{id:\\d+}/query")
    public R<List<Map<String, Object>>> query(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        long start = System.currentTimeMillis();
        LocalDateTime startTime = LocalDateTime.now();
        RuleDbDatasource datasource = datasourceService.getById(id);
        String sql = body.get("sql") == null ? null : String.valueOf(body.get("sql"));
        List<Object> params = body.get("params") instanceof List ? (List<Object>) body.get("params") : Collections.emptyList();
        Integer maxRows = body.get("maxRows") instanceof Number ? ((Number) body.get("maxRows")).intValue() : 100;
        try {
            List<Map<String, Object>> rows = dbConnectPools.query(id, sql, params, maxRows);
            logDatabaseCall(datasource, "QUERY", buildDatabaseRequest(datasource, sql, params, maxRows, null, startTime),
                    buildDatabaseResponse("SUCCESS", null, rows, null, null, startTime), true, null, System.currentTimeMillis() - start);
            return R.ok(rows);
        } catch (Exception e) {
            logDatabaseCall(datasource, "QUERY", buildDatabaseRequest(datasource, sql, params, maxRows, null, startTime),
                    buildDatabaseResponse("FAILED", null, null, null, e.getMessage(), startTime), false, e.getMessage(), System.currentTimeMillis() - start);
            return R.fail("查询失败：" + e.getMessage());
        }
    }

    private void logDatabaseCall(RuleDbDatasource datasource, String actionType, Object requestDetail,
                                 Object responseDetail, boolean success, String errorMessage, long costTimeMs) {
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
            log.setRequestUrl(datasource.getJdbcUrl());
        } else {
            log.setRequestUrl(null);
        }
        log.setRequestMethod("SQL");
        log.setRequestBody(runtimeCallLogService.toJson(requestDetail));
        log.setResponseStatus(success ? 200 : 500);
        log.setResponseBody(runtimeCallLogService.toJson(responseDetail));
        log.setSuccess(success ? 1 : 0);
        log.setErrorMessage(errorMessage);
        log.setCostTimeMs(costTimeMs);
        runtimeCallLogService.safeSave(log);
    }

    private Map<String, Object> buildDatabaseRequest(RuleDbDatasource datasource, String sql, List<Object> params,
                                                     Integer maxRows, Object draftConfig, LocalDateTime startTime) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("connectionMode", datasource == null ? null : datasource.getConnectionMode());
        request.put("dbType", datasource == null ? null : datasource.getDbType());
        request.put("datasourceId", datasource == null ? null : datasource.getId());
        request.put("datasourceCode", datasource == null ? null : datasource.getDatasourceCode());
        request.put("datasourceName", datasource == null ? null : datasource.getDatasourceName());
        request.put("queryStatus", "RUNNING");
        request.put("startTime", startTime == null ? null : startTime.toString());
        request.put("sql", sql);
        request.put("params", params);
        request.put("paramFields", buildParamFields(params));
        request.put("maxRows", maxRows);
        request.put("draftConfig", draftConfig);
        return request;
    }

    private Map<String, Object> buildDatabaseResponse(String status, Object message,
                                                      List<Map<String, Object>> rows, Object extractedValue,
                                                      String errorMessage, LocalDateTime startTime) {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("queryStatus", status);
        response.put("startTime", startTime == null ? null : startTime.toString());
        response.put("endTime", LocalDateTime.now().toString());
        response.put("message", message);
        response.put("rowCount", rows == null ? 0 : rows.size());
        response.put("rows", rows);
        response.put("extractedValue", extractedValue);
        if (errorMessage != null) {
            response.put("errorMessage", errorMessage);
        }
        return response;
    }

    private List<Map<String, Object>> buildParamFields(List<Object> params) {
        if (params == null || params.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.ArrayList<Map<String, Object>> fields = new java.util.ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            LinkedHashMap<String, Object> field = new LinkedHashMap<>();
            field.put("index", i + 1);
            field.put("field", "param" + (i + 1));
            field.put("value", params.get(i));
            fields.add(field);
        }
        return fields;
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
