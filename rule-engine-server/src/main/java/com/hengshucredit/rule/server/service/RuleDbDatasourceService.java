package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RuleDbDatasourceService extends ServiceImpl<RuleDbDatasourceMapper, RuleDbDatasource> {

    @Resource
    private RuleProjectMapper projectMapper;

    @Resource
    private DBConnectPools dbConnectPools;

    public IPage<RuleDbDatasource> pageList(int pageNum, int pageSize, String scope, Long projectId,
                                            String datasourceCode, String datasourceName, String dbType,
                                            Integer status) {
        LambdaQueryWrapper<RuleDbDatasource> wrapper = new LambdaQueryWrapper<>();
        if (hasText(scope)) {
            wrapper.eq(RuleDbDatasource::getScope, scope);
        }
        if (projectId != null && projectId > 0) {
            wrapper.eq(RuleDbDatasource::getProjectId, projectId);
        }
        if (hasText(datasourceCode)) {
            wrapper.likeRight(RuleDbDatasource::getDatasourceCode, datasourceCode);
        }
        if (hasText(datasourceName)) {
            wrapper.like(RuleDbDatasource::getDatasourceName, datasourceName);
        }
        if (hasText(dbType)) {
            wrapper.eq(RuleDbDatasource::getDbType, dbType);
        }
        if (status != null) {
            wrapper.eq(RuleDbDatasource::getStatus, status);
        }
        wrapper.orderByDesc(RuleDbDatasource::getCreateTime);
        IPage<RuleDbDatasource> page = page(new Page<>(pageNum, pageSize), wrapper);
        fillProjectName(page.getRecords());
        return page;
    }

    public void saveWithDefaults(RuleDbDatasource datasource) {
        fillDefaults(datasource);
        save(datasource);
    }

    public void updateWithDefaults(RuleDbDatasource datasource) {
        fillDefaults(datasource);
        updateById(datasource);
        dbConnectPools.refresh(datasource.getId());
    }

    public void deleteDatasource(Long id) {
        removeById(id);
        dbConnectPools.refresh(id);
    }

    public void testConnection(Long id) throws Exception {
        RuleDbDatasource datasource = getById(id);
        if (datasource == null) {
            throw new IllegalArgumentException("数据库数据源不存在");
        }
        dbConnectPools.testConnection(datasource);
    }

    public void testConnection(RuleDbDatasource datasource) throws Exception {
        fillDefaults(datasource);
        dbConnectPools.testConnection(datasource);
    }

    private void fillDefaults(RuleDbDatasource datasource) {
        if (!hasText(datasource.getScope())) {
            datasource.setScope(RuleVariableService.SCOPE_PROJECT);
        }
        if (RuleVariableService.SCOPE_GLOBAL.equals(datasource.getScope())) {
            datasource.setProjectId(0L);
        }
        if (datasource.getProjectId() == null) {
            datasource.setProjectId(0L);
        }
        if (!hasText(datasource.getDbType())) {
            datasource.setDbType("MYSQL");
        }
        if (!hasText(datasource.getConnectionMode())) {
            datasource.setConnectionMode("DIRECT");
        }
        if (!hasText(datasource.getDriverClassName())) {
            datasource.setDriverClassName(defaultDriver(datasource.getDbType()));
        }
        if (datasource.getPort() == null || datasource.getPort() <= 0) {
            datasource.setPort(DBConnectPools.defaultPort(datasource));
        }
        if (!hasText(datasource.getJdbcUrl())) {
            String jdbcUrl = DBConnectPools.buildJdbcUrl(datasource);
            if (hasText(jdbcUrl)) {
                datasource.setJdbcUrl(jdbcUrl);
            }
        }
        if (datasource.getMaxPoolSize() == null) {
            datasource.setMaxPoolSize(5);
        }
        if (datasource.getMinIdle() == null) {
            datasource.setMinIdle(1);
        }
        if (datasource.getConnectionTimeoutMs() == null) {
            datasource.setConnectionTimeoutMs(3000);
        }
        if (datasource.getIdleTimeoutMs() == null) {
            datasource.setIdleTimeoutMs(600000);
        }
        if (!hasText(datasource.getValidationQuery())) {
            datasource.setValidationQuery("SELECT 1");
        }
        if (datasource.getSshPort() == null || datasource.getSshPort() <= 0) {
            datasource.setSshPort(22);
        }
        if (datasource.getSshTimeoutMs() == null || datasource.getSshTimeoutMs() <= 0) {
            datasource.setSshTimeoutMs(10000);
        }
        if (datasource.getStatus() == null) {
            datasource.setStatus(1);
        }
    }

    private String defaultDriver(String dbType) {
        if ("POSTGRESQL".equalsIgnoreCase(dbType)) {
            return "org.postgresql.Driver";
        }
        if ("ORACLE".equalsIgnoreCase(dbType)) {
            return "oracle.jdbc.OracleDriver";
        }
        if ("SQLSERVER".equalsIgnoreCase(dbType)) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        return "com.mysql.cj.jdbc.Driver";
    }

    private void fillProjectName(List<RuleDbDatasource> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> projectIds = list.stream()
                .filter(v -> v.getProjectId() != null && v.getProjectId() > 0)
                .map(RuleDbDatasource::getProjectId)
                .distinct()
                .collect(Collectors.toList());
        if (projectIds.isEmpty()) return;
        Map<Long, String> nameMap = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(RuleProject::getId, RuleProject::getProjectName, (a, b) -> a));
        list.forEach(v -> v.setProjectName(nameMap.get(v.getProjectId())));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
