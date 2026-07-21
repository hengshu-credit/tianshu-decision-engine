package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.server.mapper.RuleRuntimeCallLogMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RuleRuntimeCallLogService extends ServiceImpl<RuleRuntimeCallLogMapper, RuleRuntimeCallLog> {

    @Resource
    private RuntimeCallLogAsyncWriter asyncWriter;

    public IPage<RuleRuntimeCallLog> pageList(int pageNum, int pageSize, String moduleType, String actionType,
                                              String targetCode, String traceId, Integer success,
                                              LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<RuleRuntimeCallLog> wrapper = new LambdaQueryWrapper<>();
        if (hasText(moduleType)) {
            wrapper.eq(RuleRuntimeCallLog::getModuleType, moduleType);
        }
        if (hasText(actionType)) {
            wrapper.eq(RuleRuntimeCallLog::getActionType, actionType);
        }
        if (hasText(targetCode)) {
            wrapper.like(RuleRuntimeCallLog::getTargetCode, targetCode);
        }
        if (hasText(traceId)) {
            wrapper.and(w -> w.eq(RuleRuntimeCallLog::getTraceId, traceId)
                    .or().eq(RuleRuntimeCallLog::getRuleTraceId, traceId));
        }
        if (success != null) {
            wrapper.eq(RuleRuntimeCallLog::getSuccess, success);
        }
        if (startTime != null) {
            wrapper.ge(RuleRuntimeCallLog::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(RuleRuntimeCallLog::getCreateTime, endTime);
        }
        wrapper.orderByDesc(RuleRuntimeCallLog::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    public void safeSave(RuleRuntimeCallLog log) {
        if (log == null) {
            return;
        }
        if (asyncWriter != null) {
            asyncWriter.offer(log);
            return;
        }
        try {
            save(log);
        } catch (Exception ignored) {
            // 业务调用不能因为诊断日志写入失败而失败。
        }
    }

    public Map<String, Object> externalApiStats(Long projectId, Long targetRefId,
                                                LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<RuleRuntimeCallLog> wrapper = new LambdaQueryWrapper<RuleRuntimeCallLog>()
                .eq(RuleRuntimeCallLog::getModuleType, "DATASOURCE")
                .eq(RuleRuntimeCallLog::getActionType, "API_INVOKE");
        if (projectId != null) {
            wrapper.eq(RuleRuntimeCallLog::getProjectId, projectId);
        }
        if (targetRefId != null) {
            wrapper.eq(RuleRuntimeCallLog::getTargetRefId, targetRefId);
        }
        if (startTime != null) {
            wrapper.ge(RuleRuntimeCallLog::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(RuleRuntimeCallLog::getCreateTime, endTime);
        }
        wrapper.orderByAsc(RuleRuntimeCallLog::getTargetRefId)
                .orderByAsc(RuleRuntimeCallLog::getCreateTime);
        return buildExternalApiStats(list(wrapper));
    }

    Map<String, Object> buildExternalApiStats(List<RuleRuntimeCallLog> logs) {
        List<RuleRuntimeCallLog> safeLogs = logs == null ? Collections.emptyList() : logs;
        StatsAccumulator overview = new StatsAccumulator();
        Map<String, StatsAccumulator> grouped = new LinkedHashMap<>();
        for (RuleRuntimeCallLog log : safeLogs) {
            if (log == null) continue;
            overview.add(log);
            String key = log.getTargetRefId() == null
                    ? "CODE:" + String.valueOf(log.getTargetCode()) : "ID:" + log.getTargetRefId();
            grouped.computeIfAbsent(key, ignored -> new StatsAccumulator()).add(log);
        }
        List<Map<String, Object>> providers = new ArrayList<>();
        for (StatsAccumulator value : grouped.values()) {
            providers.add(value.toMap(true));
        }
        providers.sort(Comparator.comparing(item -> String.valueOf(item.get("targetCode")),
                Comparator.nullsLast(String::compareTo)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", overview.toMap(false));
        result.put("providers", providers);
        return result;
    }

    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return JSON.toJSONString(value);
        } catch (StackOverflowError e) {
            return "{\"error\":\"JSON_SERIALIZE_STACK_OVERFLOW\"}";
        } catch (Exception e) {
            return "{\"error\":\"JSON_SERIALIZE_FAILED\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static class StatsAccumulator {
        private Long targetRefId;
        private String targetCode;
        private String targetName;
        private long totalInvocations;
        private long queryCount;
        private long requestSuccessCount;
        private long foundCount;
        private long cacheHitCount;
        private long cacheMissCount;
        private long cacheKeyIncompleteCount;
        private long costTimeTotal;
        private final List<Long> queryCosts = new ArrayList<>();

        private void add(RuleRuntimeCallLog log) {
            if (targetRefId == null) targetRefId = log.getTargetRefId();
            if (targetCode == null) targetCode = log.getTargetCode();
            if (targetName == null) targetName = log.getTargetName();
            totalInvocations++;
            if ("HIT".equals(log.getCacheStatus())) cacheHitCount++;
            if ("MISS".equals(log.getCacheStatus())) cacheMissCount++;
            if ("CACHE_KEY_INCOMPLETE".equals(log.getCacheStatus())) cacheKeyIncompleteCount++;
            if (!Integer.valueOf(1).equals(log.getProviderRequest())) return;
            queryCount++;
            if (Integer.valueOf(1).equals(log.getRequestSuccess())) requestSuccessCount++;
            if (Integer.valueOf(1).equals(log.getFound())) foundCount++;
            if (log.getCostTimeMs() != null) {
                long cost = Math.max(log.getCostTimeMs(), 0L);
                queryCosts.add(cost);
                costTimeTotal += cost;
            }
        }

        private Map<String, Object> toMap(boolean includeTarget) {
            Map<String, Object> result = new LinkedHashMap<>();
            if (includeTarget) {
                result.put("targetRefId", targetRefId);
                result.put("targetCode", targetCode);
                result.put("targetName", targetName);
            }
            result.put("totalInvocations", totalInvocations);
            result.put("queryCount", queryCount);
            result.put("requestSuccessCount", requestSuccessCount);
            result.put("foundCount", foundCount);
            result.put("cacheHitCount", cacheHitCount);
            result.put("cacheMissCount", cacheMissCount);
            result.put("cacheKeyIncompleteCount", cacheKeyIncompleteCount);
            result.put("cacheHitRate", rate(cacheHitCount, cacheHitCount + cacheMissCount));
            result.put("requestSuccessRate", rate(requestSuccessCount, queryCount));
            result.put("failureRate", queryCount == 0 ? 0D : 1D - rate(requestSuccessCount, queryCount));
            result.put("foundRate", rate(foundCount, queryCount));
            result.put("avgCostTimeMs", queryCosts.isEmpty() ? 0D : (double) costTimeTotal / queryCosts.size());
            result.put("p95CostTimeMs", percentile(queryCosts, 0.95D));
            result.put("p99CostTimeMs", percentile(queryCosts, 0.99D));
            return result;
        }

        private double rate(long numerator, long denominator) {
            return denominator <= 0 ? 0D : (double) numerator / denominator;
        }

        private long percentile(List<Long> values, double percentile) {
            if (values.isEmpty()) return 0L;
            List<Long> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
            return sorted.get(index);
        }
    }
}
