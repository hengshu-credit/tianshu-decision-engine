package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.server.mapper.RuleExecutionLogMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则执行日志服务，提供批量插入等能力
 */
@Service
public class RuleExecutionLogService extends ServiceImpl<RuleExecutionLogMapper, RuleExecutionLog> {

    @Resource
    private ProjectFilterService projectFilterService;

    public IPage<RuleExecutionLog> pageList(int pageNum, int pageSize, String modelType, String source,
                                            String projectCode, String projectName, String ruleCode,
                                            String traceId, String authType, String authCode, String tokenCode,
                                            LocalDateTime startTime, LocalDateTime endTime) {
        ProjectFilterService.ProjectMatches projectMatches = projectFilterService.resolve(null, projectName);
        if (projectMatches.isActive() && projectMatches.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }
        LambdaQueryWrapper<RuleExecutionLog> wrapper = new LambdaQueryWrapper<>();
        if (hasText(modelType)) wrapper.eq(RuleExecutionLog::getModelType, modelType);
        if (hasText(source)) wrapper.eq(RuleExecutionLog::getSource, source);
        if (hasText(projectCode)) wrapper.like(RuleExecutionLog::getProjectCode, projectCode);
        if (projectMatches.isActive()) {
            wrapper.in(RuleExecutionLog::getProjectCode, projectMatches.getProjectCodes());
        }
        if (hasText(ruleCode)) wrapper.eq(RuleExecutionLog::getRuleCode, ruleCode);
        if (hasText(traceId)) wrapper.eq(RuleExecutionLog::getTraceId, traceId);
        if (hasText(authType)) wrapper.eq(RuleExecutionLog::getAuthType, authType);
        if (hasText(authCode)) wrapper.like(RuleExecutionLog::getAuthCode, authCode);
        if (hasText(tokenCode)) wrapper.like(RuleExecutionLog::getTokenCode, tokenCode);
        if (startTime != null) wrapper.ge(RuleExecutionLog::getCreateTime, startTime);
        if (endTime != null) wrapper.le(RuleExecutionLog::getCreateTime, endTime);
        wrapper.orderByDesc(RuleExecutionLog::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    public Map<String, Object> ruleSetStats(String projectCode, String projectName, String ruleCode,
                                            LocalDateTime startTime, LocalDateTime endTime) {
        ProjectFilterService.ProjectMatches projectMatches = projectFilterService.resolve(null, projectName);
        if (projectMatches.isActive() && projectMatches.isEmpty()) {
            return buildRuleSetStats(Collections.emptyList(), ruleCode);
        }
        LambdaQueryWrapper<RuleExecutionLog> wrapper = new LambdaQueryWrapper<RuleExecutionLog>()
                .isNotNull(RuleExecutionLog::getTraceInfo);
        if (hasText(projectCode)) wrapper.like(RuleExecutionLog::getProjectCode, projectCode);
        if (projectMatches.isActive()) {
            wrapper.in(RuleExecutionLog::getProjectCode, projectMatches.getProjectCodes());
        }
        if (startTime != null) wrapper.ge(RuleExecutionLog::getCreateTime, startTime);
        if (endTime != null) wrapper.le(RuleExecutionLog::getCreateTime, endTime);
        wrapper.orderByAsc(RuleExecutionLog::getCreateTime);
        return buildRuleSetStats(list(wrapper), ruleCode);
    }

    Map<String, Object> buildRuleSetStats(List<RuleExecutionLog> logs, String ruleCodeFilter) {
        Map<String, RuleSetAccumulator> accumulators = new LinkedHashMap<>();
        OverallAccumulator overview = new OverallAccumulator();
        for (RuleExecutionLog log : logs == null ? Collections.<RuleExecutionLog>emptyList() : logs) {
            if (log == null || !hasText(log.getTraceInfo())) continue;
            try {
                Object parsed = JSON.parse(log.getTraceInfo());
                if (parsed instanceof JSONArray) {
                    for (Object item : (JSONArray) parsed) collectFrame(asObject(item), ruleCodeFilter, accumulators, overview);
                } else {
                    collectFrame(asObject(parsed), ruleCodeFilter, accumulators, overview);
                }
            } catch (Exception ignored) {
                // 单条历史追踪数据损坏不应阻断整个统计看板。
            }
        }
        List<Map<String, Object>> ruleSets = new ArrayList<>();
        for (RuleSetAccumulator accumulator : accumulators.values()) ruleSets.add(accumulator.toMap());
        ruleSets.sort(Comparator.comparing(item -> String.valueOf(item.get("ruleCode"))));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", overview.toMap());
        result.put("ruleSets", ruleSets);
        return result;
    }

    private void collectFrame(JSONObject frame, String ruleCodeFilter,
                              Map<String, RuleSetAccumulator> accumulators,
                              OverallAccumulator overview) {
        if (frame == null) return;
        if ("RULE_SET".equalsIgnoreCase(frame.getString("modelType"))
                && (!hasText(ruleCodeFilter) || ruleCodeFilter.equals(frame.getString("ruleCode")))) {
            JSONArray events = frame.getJSONArray("events");
            boolean observed = false;
            Boolean summaryHit = null;
            boolean anyItemHit = false;
            List<JSONObject> itemEvents = new ArrayList<>();
            if (events != null) {
                for (Object eventObject : events) {
                    JSONObject event = asObject(eventObject);
                    if (event == null || !event.getBooleanValue("evaluated")) continue;
                    if ("RULE_SET_SUMMARY".equals(event.getString("type"))) {
                        observed = true;
                        summaryHit = event.getBooleanValue("hit");
                    } else if ("RULE_SET_ITEM".equals(event.getString("type"))) {
                        observed = true;
                        itemEvents.add(event);
                        if (event.getBooleanValue("hit")) anyItemHit = true;
                    }
                }
            }
            if (observed) {
                String key = frame.getLong("ruleId") == null
                        ? "CODE:" + frame.getString("ruleCode") : "ID:" + frame.getLong("ruleId");
                RuleSetAccumulator accumulator = accumulators.computeIfAbsent(key, ignored -> new RuleSetAccumulator());
                boolean hit = summaryHit == null ? anyItemHit : summaryHit;
                accumulator.addFrame(frame, hit, itemEvents);
                overview.add(frame, hit);
            }
        }
        JSONArray children = frame.getJSONArray("children");
        if (children != null) {
            for (Object child : children) collectFrame(asObject(child), ruleCodeFilter, accumulators, overview);
        }
    }

    private JSONObject asObject(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof Map) return new JSONObject((Map<String, Object>) value);
        return null;
    }

    private static class OverallAccumulator {
        private long evaluationCount;
        private long hitCount;
        private long failureCount;
        private long totalCost;
        private final List<Long> costs = new ArrayList<>();

        private void add(JSONObject frame, boolean hit) {
            evaluationCount++;
            if (hit) hitCount++;
            if ("FAILED".equalsIgnoreCase(frame.getString("status"))) failureCount++;
            long cost = Math.max(frame.getLongValue("durationMs"), 0L);
            costs.add(cost);
            totalCost += cost;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("evaluationCount", evaluationCount);
            result.put("hitCount", hitCount);
            result.put("failureCount", failureCount);
            result.put("hitRate", rate(hitCount, evaluationCount));
            result.put("failureRate", rate(failureCount, evaluationCount));
            result.put("avgCostTimeMs", costs.isEmpty() ? 0D : (double) totalCost / costs.size());
            result.put("p95CostTimeMs", percentile(costs, 0.95D));
            result.put("p99CostTimeMs", percentile(costs, 0.99D));
            return result;
        }
    }

    private static class RuleSetAccumulator {
        private Long ruleId;
        private String ruleCode;
        private String ruleName;
        private long evaluationCount;
        private long hitCount;
        private long failureCount;
        private long totalCost;
        private final List<Long> costs = new ArrayList<>();
        private final Map<String, ItemAccumulator> items = new LinkedHashMap<>();

        private void addFrame(JSONObject frame, boolean hit, List<JSONObject> itemEvents) {
            if (ruleId == null) ruleId = frame.getLong("ruleId");
            if (ruleCode == null) ruleCode = frame.getString("ruleCode");
            if (ruleName == null) ruleName = frame.getString("ruleName");
            evaluationCount++;
            if (hit) hitCount++;
            if ("FAILED".equalsIgnoreCase(frame.getString("status"))) failureCount++;
            long cost = Math.max(frame.getLongValue("durationMs"), 0L);
            costs.add(cost);
            totalCost += cost;
            for (JSONObject event : itemEvents) {
                String itemCode = event.getString("ruleCode");
                String key = hasText(itemCode) ? itemCode : "NAME:" + event.getString("ruleName");
                items.computeIfAbsent(key, ignored -> new ItemAccumulator()).add(event);
            }
        }

        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ruleId", ruleId);
            result.put("ruleCode", ruleCode);
            result.put("ruleName", ruleName);
            result.put("evaluationCount", evaluationCount);
            result.put("hitCount", hitCount);
            result.put("failureCount", failureCount);
            result.put("hitRate", rate(hitCount, evaluationCount));
            result.put("failureRate", rate(failureCount, evaluationCount));
            result.put("avgCostTimeMs", costs.isEmpty() ? 0D : (double) totalCost / costs.size());
            result.put("p95CostTimeMs", percentile(costs, 0.95D));
            result.put("p99CostTimeMs", percentile(costs, 0.99D));
            List<Map<String, Object>> itemRows = new ArrayList<>();
            for (ItemAccumulator item : items.values()) itemRows.add(item.toMap());
            itemRows.sort(Comparator.comparing(item -> String.valueOf(item.get("ruleCode"))));
            result.put("items", itemRows);
            return result;
        }
    }

    private static class ItemAccumulator {
        private String ruleCode;
        private String ruleName;
        private long evaluationCount;
        private long hitCount;

        private void add(JSONObject event) {
            if (ruleCode == null) ruleCode = event.getString("ruleCode");
            if (ruleName == null) ruleName = event.getString("ruleName");
            evaluationCount++;
            if (event.getBooleanValue("hit")) hitCount++;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ruleCode", ruleCode);
            result.put("ruleName", ruleName);
            result.put("evaluationCount", evaluationCount);
            result.put("hitCount", hitCount);
            result.put("hitRate", rate(hitCount, evaluationCount));
            return result;
        }
    }

    private static double rate(long numerator, long denominator) {
        return denominator <= 0 ? 0D : (double) numerator / denominator;
    }

    private static long percentile(List<Long> values, double percentile) {
        if (values == null || values.isEmpty()) return 0L;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1));
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
