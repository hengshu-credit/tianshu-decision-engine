package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.server.mapper.RuleRuntimeCallLogMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

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
        return pageList(pageNum, pageSize, moduleType, actionType, targetCode, traceId, null,
                success, startTime, endTime);
    }

    public IPage<RuleRuntimeCallLog> pageList(int pageNum, int pageSize, String moduleType, String actionType,
                                              String targetCode, String traceId, String callId, Integer success,
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
        if (hasText(callId)) {
            wrapper.eq(RuleRuntimeCallLog::getCallId, callId);
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

    /** 返回一次调用的原始报文和稳定关联键，供业务分析系统按日志 ID 直接取数。 */
    public Map<String, Object> payload(Long id) {
        RuleRuntimeCallLog log = id == null ? null : getById(id);
        return payloadWithAssignments(log);
    }

    /**
     * 按一次逻辑外数调用的稳定 ID 取汇总报文。重试 attempt 共用 callId，优先返回 API_INVOKE 汇总行。
     */
    public Map<String, Object> payloadByCallId(String callId) {
        return payloadByCallId(callId, null);
    }

    /** 按一次逻辑调用 ID 取报文；projectId 非空时同时做项目隔离。 */
    public Map<String, Object> payloadByCallId(String callId, Long projectId) {
        if (!hasText(callId)) return Collections.emptyMap();
        List<RuleRuntimeCallLog> logs = payloadLogsByCallId(callId, projectId, "API_INVOKE");
        if (logs.isEmpty()) {
            logs = payloadLogsByCallId(callId, projectId, "API_ATTEMPT");
        }
        return payloadWithAssignments(logs.isEmpty() ? null : logs.get(0));
    }

    private Map<String, Object> payloadWithAssignments(RuleRuntimeCallLog log) {
        Map<String, Object> result = payload(log);
        if (log == null || !"API_INVOKE".equals(log.getActionType()) || !hasText(log.getCallId())) return result;
        LambdaQueryWrapper<RuleRuntimeCallLog> query = new LambdaQueryWrapper<RuleRuntimeCallLog>()
                .eq(RuleRuntimeCallLog::getCallId, log.getCallId())
                .eq(RuleRuntimeCallLog::getModuleType, "DATASOURCE")
                .eq(RuleRuntimeCallLog::getActionType, "API_ASSIGNMENT")
                .orderByAsc(RuleRuntimeCallLog::getCreateTime).orderByAsc(RuleRuntimeCallLog::getId);
        if (log.getProjectId() == null) query.isNull(RuleRuntimeCallLog::getProjectId);
        else query.eq(RuleRuntimeCallLog::getProjectId, log.getProjectId());
        List<Map<String, Object>> steps = traceSteps(result);
        mergeAssignmentSteps(steps, list(query));
        result.put("traceSteps", steps);
        return result;
    }

    private List<RuleRuntimeCallLog> payloadLogsByCallId(String callId, Long projectId, String actionType) {
        LambdaQueryWrapper<RuleRuntimeCallLog> wrapper = new LambdaQueryWrapper<RuleRuntimeCallLog>()
                .eq(RuleRuntimeCallLog::getCallId, callId)
                .eq(RuleRuntimeCallLog::getModuleType, "DATASOURCE")
                .eq(RuleRuntimeCallLog::getActionType, actionType)
                .orderByDesc(RuleRuntimeCallLog::getCreateTime)
                .orderByDesc(RuleRuntimeCallLog::getId)
                .last("LIMIT 1");
        if (projectId != null) {
            wrapper.eq(RuleRuntimeCallLog::getProjectId, projectId);
        }
        return list(wrapper);
    }

    /** 按根 Trace 一次取出该规则执行内所有外数逻辑调用的原始报文。 */
    public List<Map<String, Object>> payloadsByRootTraceId(String rootTraceId) {
        return payloadsByRootTraceId(rootTraceId, null);
    }

    /** 取回溯所需的同一根执行下全部来源日志；调用方只应使用这些日志装载离线结果。 */
    public List<RuleRuntimeCallLog> sourceLogsByRootTraceId(String rootTraceId, Long projectId) {
        if (!hasText(rootTraceId)) return Collections.emptyList();
        LambdaQueryWrapper<RuleRuntimeCallLog> wrapper = new LambdaQueryWrapper<RuleRuntimeCallLog>()
                .and(w -> w.eq(RuleRuntimeCallLog::getRootTraceId, rootTraceId)
                        .or().eq(RuleRuntimeCallLog::getRuleTraceId, rootTraceId)
                        .or().eq(RuleRuntimeCallLog::getTraceId, rootTraceId))
                .in(RuleRuntimeCallLog::getActionType,
                        "API_INVOKE", "API_ASSIGNMENT", "DB_VARIABLE_QUERY",
                        "LIST_VARIABLE_MATCH", "MODEL_EXECUTE")
                .orderByAsc(RuleRuntimeCallLog::getCreateTime)
                .orderByAsc(RuleRuntimeCallLog::getId);
        if (projectId != null) wrapper.eq(RuleRuntimeCallLog::getProjectId, projectId);
        return list(wrapper);
    }

    /** 按根 Trace 取外数报文；projectId 非空时同时做项目隔离。 */
    public List<Map<String, Object>> payloadsByRootTraceId(String rootTraceId, Long projectId) {
        if (!hasText(rootTraceId)) return Collections.emptyList();
        LambdaQueryWrapper<RuleRuntimeCallLog> wrapper = new LambdaQueryWrapper<RuleRuntimeCallLog>()
                .eq(RuleRuntimeCallLog::getRootTraceId, rootTraceId)
                .eq(RuleRuntimeCallLog::getModuleType, "DATASOURCE")
                .eq(RuleRuntimeCallLog::getActionType, "API_INVOKE")
                .orderByAsc(RuleRuntimeCallLog::getCreateTime)
                .orderByAsc(RuleRuntimeCallLog::getId);
        if (projectId != null) {
            wrapper.eq(RuleRuntimeCallLog::getProjectId, projectId);
        }
        List<RuleRuntimeCallLog> summaries = list(wrapper);
        List<String> callIds = summaries.stream().map(RuleRuntimeCallLog::getCallId)
                .filter(this::hasText).distinct().toList();
        Map<String, List<RuleRuntimeCallLog>> assignmentsByCallId = new LinkedHashMap<>();
        if (!callIds.isEmpty()) {
            LambdaQueryWrapper<RuleRuntimeCallLog> assignmentWrapper = new LambdaQueryWrapper<RuleRuntimeCallLog>()
                    .in(RuleRuntimeCallLog::getCallId, callIds)
                    .eq(RuleRuntimeCallLog::getModuleType, "DATASOURCE")
                    .eq(RuleRuntimeCallLog::getActionType, "API_ASSIGNMENT")
                    .orderByAsc(RuleRuntimeCallLog::getCreateTime)
                    .orderByAsc(RuleRuntimeCallLog::getId);
            if (projectId != null) assignmentWrapper.eq(RuleRuntimeCallLog::getProjectId, projectId);
            for (RuleRuntimeCallLog assignment : list(assignmentWrapper)) {
                assignmentsByCallId.computeIfAbsent(assignment.getCallId(), ignored -> new ArrayList<>())
                        .add(assignment);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (RuleRuntimeCallLog log : summaries) {
            Map<String, Object> item = payload(log);
            List<Map<String, Object>> steps = traceSteps(item);
            List<RuleRuntimeCallLog> assignments = assignmentsByCallId.getOrDefault(log.getCallId(), List.of())
                    .stream().filter(assignment -> java.util.Objects.equals(log.getProjectId(), assignment.getProjectId()))
                    .toList();
            mergeAssignmentSteps(steps, assignments);
            item.put("traceSteps", steps);
            if (!item.isEmpty()) result.add(item);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> traceSteps(Map<String, Object> payload) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (payload == null) return result;
        Object existing = payload.get("traceSteps");
        if (existing instanceof List) {
            for (Object step : (List<?>) existing) {
                if (step instanceof Map) result.add(new LinkedHashMap<>((Map<String, Object>) step));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void mergeAssignmentSteps(List<Map<String, Object>> target, List<RuleRuntimeCallLog> assignments) {
        if (target == null || assignments == null) return;
        for (RuleRuntimeCallLog assignment : assignments) {
            Object parsed = parseTraceSteps(assignment.getTraceSteps());
            if (!(parsed instanceof List)) continue;
            for (Object step : (List<?>) parsed) {
                if (!(step instanceof Map)) continue;
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) step);
                copy.put("sequence", target.size() + 1);
                target.add(copy);
            }
        }
    }

    private Map<String, Object> payload(RuleRuntimeCallLog log) {
        if (log == null) return Collections.emptyMap();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", log.getId());
        result.put("callId", log.getCallId());
        result.put("traceId", log.getTraceId());
        result.put("rootTraceId", log.getRootTraceId());
        result.put("ruleTraceId", log.getRuleTraceId());
        result.put("requestId", log.getRequestId());
        result.put("targetRefId", log.getTargetRefId());
        result.put("targetCode", log.getTargetCode());
        result.put("requestMethod", log.getRequestMethod());
        result.put("requestUrl", log.getRequestUrl());
        result.put("requestBody", log.getRawRequestBody() == null
                ? log.getRequestBody() : log.getRawRequestBody());
        result.put("originalRequestBody", log.getOriginalRequestBody());
        result.put("traceSteps", parseTraceSteps(log.getTraceSteps()));
        result.put("rawRequestMetadata", parseMetadata(log.getRawRequestMetadata()));
        result.put("responseStatus", log.getResponseStatus());
        result.put("responseBody", log.getRawResponseBody() == null
                ? log.getResponseBody() : log.getRawResponseBody());
        result.put("originalResponseBody", log.getOriginalResponseBody());
        result.put("rawResponseMetadata", parseMetadata(log.getRawResponseMetadata()));
        result.put("rawRequestAvailable", log.getRawRequestBody() != null);
        result.put("rawResponseAvailable", log.getRawResponseBody() != null);
        result.put("originalRequestAvailable", log.getOriginalRequestBody() != null);
        result.put("originalResponseAvailable", log.getOriginalResponseBody() != null);
        result.put("rawPayloadAvailable", log.getRawRequestBody() != null || log.getRawResponseBody() != null);
        result.put("success", log.getSuccess());
        result.put("providerRequest", log.getProviderRequest());
        result.put("attemptNo", log.getAttemptNo());
        result.put("createTime", log.getCreateTime());
        return result;
    }

    private Object parseTraceSteps(String value) {
        if (value == null || value.isBlank()) return Collections.emptyList();
        try {
            Object parsed = JSON.parse(value);
            return parsed instanceof List ? parsed : Collections.emptyList();
        } catch (RuntimeException ignored) {
            return Collections.singletonList(Collections.singletonMap("status", "TRACE_STEPS_INVALID"));
        }
    }

    private Object parseMetadata(String value) {
        if (value == null || value.isBlank()) return Collections.emptyMap();
        try {
            return JSON.parseObject(value);
        } catch (RuntimeException ignored) {
            return Collections.singletonMap("status", "METADATA_INVALID");
        }
    }

    public void safeSave(RuleRuntimeCallLog log) {
        if (log == null) {
            return;
        }
        if (log.getRootTraceId() != null && "API_INVOKE".equals(log.getActionType()) && log.getHistoryFields() != null) {
            try {
                if (!save(log)) throw new IllegalStateException("insert returned false");
            } catch (RuntimeException failure) {
                throw new HistoryLogWriteException("外数历史结果日志写入失败，不能将本次进件作为完整统计事实", failure);
            }
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

    public static final class HistoryLogWriteException extends IllegalStateException {
        public HistoryLogWriteException(String message, Throwable cause) { super(message, cause); }
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
