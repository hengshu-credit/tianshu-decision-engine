package com.bjjw.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjjw.rule.model.entity.RuleDbDatasource;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.DBConnectPools;
import com.bjjw.rule.server.service.RuleDbDatasourceService;
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
        try {
            datasourceService.testConnection(id);
            return R.ok("连接成功");
        } catch (Exception e) {
            return R.fail("连接失败：" + e.getMessage());
        }
    }

    @PostMapping("/test")
    public R<String> testConnection(@RequestBody RuleDbDatasource datasource) {
        try {
            datasourceService.testConnection(datasource);
            return R.ok("连接成功");
        } catch (Exception e) {
            return R.fail("连接失败：" + e.getMessage());
        }
    }

    @PostMapping("/{id:\\d+}/query")
    public R<List<Map<String, Object>>> query(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String sql = body.get("sql") == null ? null : String.valueOf(body.get("sql"));
            List<Object> params = body.get("params") instanceof List ? (List<Object>) body.get("params") : Collections.emptyList();
            Integer maxRows = body.get("maxRows") instanceof Number ? ((Number) body.get("maxRows")).intValue() : 100;
            return R.ok(dbConnectPools.query(id, sql, params, maxRows));
        } catch (Exception e) {
            return R.fail("查询失败：" + e.getMessage());
        }
    }
}
