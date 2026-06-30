package com.bjjw.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bjjw.rule.model.entity.RuleExternalDatasource;
import com.bjjw.rule.model.entity.RuleProject;
import com.bjjw.rule.server.mapper.RuleExternalDatasourceMapper;
import com.bjjw.rule.server.mapper.RuleProjectMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RuleExternalDatasourceService extends ServiceImpl<RuleExternalDatasourceMapper, RuleExternalDatasource> {

    @Resource
    private RuleProjectMapper projectMapper;

    public IPage<RuleExternalDatasource> pageList(int pageNum, int pageSize, String scope, Long projectId,
                                                  String datasourceCode, String datasourceName, String authType,
                                                  Integer status) {
        LambdaQueryWrapper<RuleExternalDatasource> wrapper = new LambdaQueryWrapper<>();
        if (hasText(scope)) {
            wrapper.eq(RuleExternalDatasource::getScope, scope);
        }
        if (projectId != null && projectId > 0) {
            wrapper.eq(RuleExternalDatasource::getProjectId, projectId);
        }
        if (hasText(datasourceCode)) {
            wrapper.likeRight(RuleExternalDatasource::getDatasourceCode, datasourceCode);
        }
        if (hasText(datasourceName)) {
            wrapper.like(RuleExternalDatasource::getDatasourceName, datasourceName);
        }
        if (hasText(authType)) {
            wrapper.eq(RuleExternalDatasource::getAuthType, authType);
        }
        if (status != null) {
            wrapper.eq(RuleExternalDatasource::getStatus, status);
        }
        wrapper.orderByDesc(RuleExternalDatasource::getCreateTime);
        IPage<RuleExternalDatasource> page = page(new Page<>(pageNum, pageSize), wrapper);
        fillProjectName(page.getRecords());
        return page;
    }

    public void saveWithDefaults(RuleExternalDatasource datasource) {
        fillDefaults(datasource);
        save(datasource);
    }

    public void updateWithDefaults(RuleExternalDatasource datasource) {
        fillDefaults(datasource);
        updateById(datasource);
    }

    private void fillDefaults(RuleExternalDatasource datasource) {
        if (!hasText(datasource.getScope())) {
            datasource.setScope(RuleVariableService.SCOPE_PROJECT);
        }
        if (RuleVariableService.SCOPE_GLOBAL.equals(datasource.getScope())) {
            datasource.setProjectId(0L);
        }
        if (datasource.getProjectId() == null) {
            datasource.setProjectId(0L);
        }
        if (!hasText(datasource.getProtocol())) {
            datasource.setProtocol("HTTP");
        }
        if (!hasText(datasource.getAuthType())) {
            datasource.setAuthType("NONE");
        }
        datasource.setAuthConfig(nullIfBlank(datasource.getAuthConfig()));
        if (datasource.getTokenCacheSeconds() == null) {
            datasource.setTokenCacheSeconds(0);
        }
        if (datasource.getStatus() == null) {
            datasource.setStatus(1);
        }
    }

    private void fillProjectName(List<RuleExternalDatasource> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> projectIds = list.stream()
                .filter(v -> v.getProjectId() != null && v.getProjectId() > 0)
                .map(RuleExternalDatasource::getProjectId)
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

    private String nullIfBlank(String value) {
        return hasText(value) ? value : null;
    }
}
