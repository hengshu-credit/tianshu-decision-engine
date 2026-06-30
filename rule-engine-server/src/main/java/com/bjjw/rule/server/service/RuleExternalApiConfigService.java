package com.bjjw.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bjjw.rule.model.entity.RuleExternalApiConfig;
import com.bjjw.rule.model.entity.RuleExternalDatasource;
import com.bjjw.rule.server.mapper.RuleExternalApiConfigMapper;
import com.bjjw.rule.server.mapper.RuleExternalDatasourceMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RuleExternalApiConfigService extends ServiceImpl<RuleExternalApiConfigMapper, RuleExternalApiConfig> {

    @Resource
    private RuleExternalDatasourceMapper datasourceMapper;

    public IPage<RuleExternalApiConfig> pageList(int pageNum, int pageSize, Long datasourceId, String datasourceCode,
                                                 String apiCode, String apiName, String requestMode, Integer status) {
        LambdaQueryWrapper<RuleExternalApiConfig> wrapper = new LambdaQueryWrapper<>();
        if (datasourceId != null && datasourceId > 0) {
            wrapper.eq(RuleExternalApiConfig::getDatasourceId, datasourceId);
        }
        if (hasText(datasourceCode)) {
            List<Long> ids = datasourceMapper.selectList(new LambdaQueryWrapper<RuleExternalDatasource>()
                    .likeRight(RuleExternalDatasource::getDatasourceCode, datasourceCode))
                    .stream().map(RuleExternalDatasource::getId).collect(Collectors.toList());
            if (ids.isEmpty()) {
                wrapper.eq(RuleExternalApiConfig::getDatasourceId, -1L);
            } else {
                wrapper.in(RuleExternalApiConfig::getDatasourceId, ids);
            }
        }
        if (hasText(apiCode)) {
            wrapper.likeRight(RuleExternalApiConfig::getApiCode, apiCode);
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

    public void saveWithDefaults(RuleExternalApiConfig config) {
        fillDefaults(config);
        save(config);
    }

    public void updateWithDefaults(RuleExternalApiConfig config) {
        fillDefaults(config);
        updateById(config);
    }

    public void deleteByDatasourceId(Long datasourceId) {
        remove(new LambdaQueryWrapper<RuleExternalApiConfig>()
                .eq(RuleExternalApiConfig::getDatasourceId, datasourceId));
    }

    private void fillDefaults(RuleExternalApiConfig config) {
        if (!hasText(config.getRequestMethod())) {
            config.setRequestMethod("POST");
        }
        if (!hasText(config.getContentType())) {
            config.setContentType("application/json");
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
        config.setAuthApiConfig(nullIfBlank(config.getAuthApiConfig()));
        if (config.getTokenCacheSeconds() == null) {
            config.setTokenCacheSeconds(0);
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
