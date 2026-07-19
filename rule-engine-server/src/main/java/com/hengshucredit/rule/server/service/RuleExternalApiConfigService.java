package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RuleExternalApiConfigService extends ServiceImpl<RuleExternalApiConfigMapper, RuleExternalApiConfig> {

    @Resource
    private RuleExternalDatasourceMapper datasourceMapper;

    @Resource
    private ProjectFilterService projectFilterService;

    public IPage<RuleExternalApiConfig> pageList(int pageNum, int pageSize, Long datasourceId,
                                                 String projectCode, String projectName, String datasourceCode,
                                                 String apiCode, String apiName, String requestMode, Integer status) {
        ProjectFilterService.ProjectMatches projectMatches = projectFilterService.resolve(projectCode, projectName);
        if (projectMatches.isActive() && projectMatches.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }
        LambdaQueryWrapper<RuleExternalApiConfig> wrapper = new LambdaQueryWrapper<>();
        if (datasourceId != null && datasourceId > 0) {
            wrapper.eq(RuleExternalApiConfig::getDatasourceId, datasourceId);
        }
        if (projectMatches.isActive() || hasText(datasourceCode)) {
            LambdaQueryWrapper<RuleExternalDatasource> datasourceWrapper = new LambdaQueryWrapper<>();
            if (projectMatches.isActive()) {
                datasourceWrapper.in(RuleExternalDatasource::getProjectId, projectMatches.getProjectIds());
            }
            if (hasText(datasourceCode)) {
                datasourceWrapper.like(RuleExternalDatasource::getDatasourceCode, datasourceCode);
            }
            List<Long> ids = datasourceMapper.selectList(datasourceWrapper)
                    .stream().map(RuleExternalDatasource::getId).collect(Collectors.toList());
            if (ids.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            wrapper.in(RuleExternalApiConfig::getDatasourceId, ids);
        }
        if (hasText(apiCode)) {
            wrapper.like(RuleExternalApiConfig::getApiCode, apiCode);
        }
        if (hasText(apiName)) {
            wrapper.like(RuleExternalApiConfig::getApiName, apiName);
        }
        if (hasText(requestMode)) {
            wrapper.eq(RuleExternalApiConfig::getRequestMode, requestMode);
        }
        if (status != null) {
            wrapper.eq(RuleExternalApiConfig::getStatus, status);
        }
        wrapper.orderByDesc(RuleExternalApiConfig::getCreateTime);
        IPage<RuleExternalApiConfig> page = page(new Page<>(pageNum, pageSize), wrapper);
        fillDatasourceInfo(page.getRecords());
        return page;
    }

    public RuleExternalApiConfig saveWithDefaults(RuleExternalApiConfig config) {
        fillDefaults(config);
        save(config);
        return getById(config.getId());
    }

    public RuleExternalApiConfig updateWithDefaults(RuleExternalApiConfig config) {
        fillDefaults(config);
        updateById(config);
        return getById(config.getId());
    }

    public void deleteByDatasourceId(Long datasourceId) {
        remove(new LambdaQueryWrapper<RuleExternalApiConfig>()
                .eq(RuleExternalApiConfig::getDatasourceId, datasourceId));
    }

    private void fillDefaults(RuleExternalApiConfig config) {
        if (!hasText(config.getRequestMethod())) {
            config.setRequestMethod("POST");
        }
        if (!hasText(config.getRequestMode())) {
            config.setRequestMode("SYNC");
        }
        if (!hasText(config.getAuthMode())) {
            config.setAuthMode("INHERIT");
        }
        config.setHeaderConfig(nullIfBlank(config.getHeaderConfig()));
        config.setQueryConfig(nullIfBlank(config.getQueryConfig()));
        config.setRequestMapping(nullIfBlank(config.getRequestMapping()));
        config.setResponseMapping(nullIfBlank(config.getResponseMapping()));
        config.setCacheKeyConfig(nullIfBlank(config.getCacheKeyConfig()));
        config.setSuccessCondition(nullIfBlank(config.getSuccessCondition()));
        config.setBillingCondition(nullIfBlank(config.getBillingCondition()));
        config.setAuthApiConfig(nullIfBlank(config.getAuthApiConfig()));
        config.setTestSampleParams(nullIfBlank(config.getTestSampleParams()));
        config.setAsyncPollConfig(nullIfBlank(config.getAsyncPollConfig()));
        config.setAsyncCallbackConfig(nullIfBlank(config.getAsyncCallbackConfig()));
        config.setAsyncCallbackUrl(nullIfBlank(config.getAsyncCallbackUrl()));
        config.setAsyncResultPath(nullIfBlank(config.getAsyncResultPath()));
        if ("ASYNC".equals(config.getRequestMode()) && !hasText(config.getAsyncResultMode())) {
            config.setAsyncResultMode("POLL");
        }
        if (!"ASYNC".equals(config.getRequestMode())) {
            config.setAsyncResultMode(null);
            config.setAsyncPollConfig(null);
            config.setAsyncCallbackConfig(null);
            config.setAsyncCallbackUrl(null);
            config.setAsyncResultPath(null);
        }
        if (config.getTokenCacheSeconds() == null) {
            config.setTokenCacheSeconds(0);
        }
        if (config.getResponseCacheSeconds() == null) {
            config.setResponseCacheSeconds(0);
        }
        if (config.getTimeoutMs() == null) {
            config.setTimeoutMs(3000);
        }
        if (config.getRetryCount() == null) {
            config.setRetryCount(0);
        }
        if (config.getRetryIntervalMs() == null) {
            config.setRetryIntervalMs(200);
        }
        if (!hasText(config.getExceptionStrategy())) {
            config.setExceptionStrategy("FAIL_FAST");
        }
        if (config.getStatus() == null) {
            config.setStatus(1);
        }
    }

    private void fillDatasourceInfo(List<RuleExternalApiConfig> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> datasourceIds = list.stream()
                .filter(v -> v.getDatasourceId() != null)
                .map(RuleExternalApiConfig::getDatasourceId)
                .distinct()
                .collect(Collectors.toList());
        if (datasourceIds.isEmpty()) return;
        Map<Long, RuleExternalDatasource> datasourceMap = datasourceMapper.selectBatchIds(datasourceIds).stream()
                .collect(Collectors.toMap(RuleExternalDatasource::getId, v -> v, (a, b) -> a));
        list.forEach(v -> {
            RuleExternalDatasource datasource = datasourceMap.get(v.getDatasourceId());
            if (datasource != null) {
                v.setDatasourceCode(datasource.getDatasourceCode());
                v.setDatasourceName(datasource.getDatasourceName());
            }
        });
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String nullIfBlank(String value) {
        return hasText(value) ? value : null;
    }
}
