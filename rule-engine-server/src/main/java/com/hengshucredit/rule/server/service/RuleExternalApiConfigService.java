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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Collectors;

@Service
public class RuleExternalApiConfigService extends ServiceImpl<RuleExternalApiConfigMapper, RuleExternalApiConfig> {

    @Resource
    private RuleExternalDatasourceMapper datasourceMapper;

    @Resource
    private ProjectFilterService projectFilterService;

    @Resource
    private ApiHttpClientRegistry apiHttpClientRegistry;

    @Resource
    private ExternalApiGuardRegistry externalApiGuardRegistry;

    @Resource
    private ExternalApiCircuitBreakerRegistry circuitBreakerRegistry;

    @Resource
    private ExternalApiResponseCache externalApiResponseCache;

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
        apiHttpClientRegistry.invalidate("api:" + String.valueOf(config.getId()));
        externalApiGuardRegistry.invalidate(config.getId());
        circuitBreakerRegistry.invalidate(config.getId());
        externalApiResponseCache.invalidate(config.getId());
        return getById(config.getId());
    }

    public boolean removeApiConfig(Long id) {
        boolean removed = removeById(id);
        apiHttpClientRegistry.invalidate("api:" + String.valueOf(id));
        externalApiGuardRegistry.invalidate(id);
        circuitBreakerRegistry.invalidate(id);
        externalApiResponseCache.invalidate(id);
        return removed;
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
        config.setRetryCondition(nullIfBlank(config.getRetryCondition()));
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
        defaultInteger(config.getTokenCacheSeconds(), config::setTokenCacheSeconds,
                0, 0, 604800, "Token缓存秒数");
        defaultInteger(config.getResponseCacheSeconds(), config::setResponseCacheSeconds,
                0, 0, 604800, "响应缓存秒数");
        defaultInteger(config.getTimeoutMs(), config::setTimeoutMs,
                3000, 100, 600000, "API总超时");
        defaultInteger(config.getMaxConnections(), config::setMaxConnections, 100, 1, 10000, "最大连接数");
        defaultInteger(config.getMaxConnectionsPerRoute(), config::setMaxConnectionsPerRoute,
                100, 1, config.getMaxConnections(), "单路由最大连接数");
        defaultInteger(config.getConnectionRequestTimeoutMs(), config::setConnectionRequestTimeoutMs,
                100, 1, 600000, "获取连接超时");
        defaultInteger(config.getConnectTimeoutMs(), config::setConnectTimeoutMs,
                500, 1, 600000, "建连超时");
        defaultInteger(config.getReadTimeoutMs(), config::setReadTimeoutMs,
                config.getTimeoutMs(), 1, 600000, "读取超时");
        defaultInteger(config.getIdleConnectionTimeoutSeconds(), config::setIdleConnectionTimeoutSeconds,
                30, 1, 3600, "空闲连接清理秒数");
        defaultInteger(config.getConnectionTtlSeconds(), config::setConnectionTtlSeconds,
                300, 1, 86400, "连接TTL秒数");
        if (config.getQpsLimit() != null && config.getQpsLimit().compareTo(BigDecimal.ZERO) <= 0) {
            config.setQpsLimit(null);
        }
        if (config.getQpsLimit() != null && config.getQpsLimit().compareTo(new BigDecimal("100000")) > 0) {
            throw new IllegalArgumentException("API QPS不能大于100000");
        }
        if (config.getQpsLimit() != null) {
            int minimumBurst = Math.max(1, config.getQpsLimit().setScale(0, RoundingMode.CEILING).intValue());
            defaultInteger(config.getBurstCapacity(), config::setBurstCapacity,
                    minimumBurst, minimumBurst, 1000000, "突发容量");
        } else {
            config.setBurstCapacity(null);
        }
        defaultInteger(config.getMaxConcurrent(), config::setMaxConcurrent, 50, 1, 10000, "最大并发");
        defaultInteger(config.getConcurrentWaitTimeoutMs(), config::setConcurrentWaitTimeoutMs,
                0, 0, 60000, "并发等待超时");
        defaultInteger(config.getTokenRefreshAheadSeconds(), config::setTokenRefreshAheadSeconds,
                60, 0, 3600, "Token提前刷新秒数");
        defaultSwitch(config.getTokenRefreshOnUnauthorized(), config::setTokenRefreshOnUnauthorized, 1);
        defaultSwitch(config.getTokenLogEnabled(), config::setTokenLogEnabled, 1);
        defaultInteger(config.getRetryCount(), config::setRetryCount,
                0, 0, 10, "重试次数");
        defaultInteger(config.getRetryIntervalMs(), config::setRetryIntervalMs,
                200, 0, 60000, "重试间隔");
        config.setRetryStatusCodes(hasText(config.getRetryStatusCodes())
                ? config.getRetryStatusCodes().trim() : "502,503,504");
        validateRetryStatusCodes(config.getRetryStatusCodes());
        defaultSwitch(config.getRetryOnConnectionError(), config::setRetryOnConnectionError, 1);
        defaultSwitch(config.getRetryOnTimeout(), config::setRetryOnTimeout, 0);
        if (config.getRetryBackoffMultiplier() == null) {
            config.setRetryBackoffMultiplier(new BigDecimal("2"));
        }
        if (config.getRetryBackoffMultiplier().compareTo(BigDecimal.ONE) < 0
                || config.getRetryBackoffMultiplier().compareTo(new BigDecimal("10")) > 0) {
            throw new IllegalArgumentException("重试退避倍数必须在1到10之间");
        }
        defaultInteger(config.getRetryMaxIntervalMs(), config::setRetryMaxIntervalMs,
                1000, 0, 60000, "最大重试间隔");
        defaultSwitch(config.getCircuitBreakerEnabled(), config::setCircuitBreakerEnabled, 1);
        defaultInteger(config.getCircuitFailureRate(), config::setCircuitFailureRate,
                50, 1, 100, "熔断失败率");
        defaultInteger(config.getCircuitMinCalls(), config::setCircuitMinCalls,
                20, 1, 10000, "熔断最小调用数");
        defaultInteger(config.getCircuitWindowSize(), config::setCircuitWindowSize,
                50, config.getCircuitMinCalls(), 10000, "熔断窗口大小");
        defaultInteger(config.getCircuitOpenSeconds(), config::setCircuitOpenSeconds,
                10, 1, 3600, "熔断打开秒数");
        defaultInteger(config.getCircuitHalfOpenCalls(), config::setCircuitHalfOpenCalls,
                5, 1, 1000, "半开探测调用数");
        defaultInteger(config.getResponseCacheMaxSize(), config::setResponseCacheMaxSize,
                10000, 1, 1000000, "响应缓存最大条数");
        defaultInteger(config.getResponseCacheMaxBytes(), config::setResponseCacheMaxBytes,
                1048576, 1024, 10485760, "单条响应缓存最大字节数");
        defaultSwitch(config.getResponseCacheRedisEnabled(), config::setResponseCacheRedisEnabled, 0);
        defaultInteger(config.getStaleCacheSeconds(), config::setStaleCacheSeconds,
                0, 0, 86400, "过期缓存兜底秒数");
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

    private void defaultInteger(Integer value, java.util.function.Consumer<Integer> setter,
                                int defaultValue, int min, int max, String name) {
        int resolved = value == null ? defaultValue : value;
        if (resolved < min || resolved > max) {
            throw new IllegalArgumentException(name + "必须在" + min + "到" + max + "之间");
        }
        setter.accept(resolved);
    }

    private void defaultSwitch(Integer value, java.util.function.Consumer<Integer> setter, int defaultValue) {
        int resolved = value == null ? defaultValue : value;
        if (resolved != 0 && resolved != 1) {
            throw new IllegalArgumentException("开关值只能是0或1");
        }
        setter.accept(resolved);
    }

    private void validateRetryStatusCodes(String value) {
        String[] items = value.split(",", -1);
        if (items.length > 32) {
            throw new IllegalArgumentException("重试状态码最多配置32项");
        }
        for (String item : items) {
            try {
                int status = Integer.parseInt(item.trim());
                if (status < 100 || status > 599) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("重试状态码必须是100到599之间的整数: " + item);
            }
        }
    }
}
