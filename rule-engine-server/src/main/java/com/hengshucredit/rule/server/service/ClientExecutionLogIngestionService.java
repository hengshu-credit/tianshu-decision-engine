package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.trace.TraceIdGenerator;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleTraceRegistry;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** HTTP 与 Kafka 共用的客户端诊断日志边界。登记、日志和计费在同一数据库事务中提交。 */
@Service
public class ClientExecutionLogIngestionService {
    @Resource
    private RuleExecutionLogService logService;
    @Resource
    private RuleBillingService billingService;
    @Resource
    private RuleTraceRegistryService traceRegistryService;
    @Resource
    private RuleProjectService projectService;

    @Transactional
    public int ingestHttp(List<RuleExecutionLog> logs, ProjectAuthContext authContext) {
        if (logs == null || logs.isEmpty()) return 0;
        if (authContext == null || authContext.getProjectId() == null) {
            throw invalid(401, "Unauthorized project token");
        }
        return ingest(logs, authContext, false);
    }

    @Transactional
    public int ingestKafka(RuleExecutionLog log, Long projectId) {
        RuleProject project = projectService.getById(projectId);
        if (project == null || !Integer.valueOf(1).equals(project.getStatus())) {
            throw invalid(403, "Kafka log project is not available");
        }
        if (log == null || !hasText(log.getTraceId())) {
            throw invalid(400, "Kafka log requires a traceId");
        }
        // 项目来自管理员配置的 topic 绑定，不接受载荷中的鉴权凭据归因。
        ProjectAuthContext scope = ProjectAuthContext.temporary(projectId, project.getProjectCode(),
                null, null, null, null, null, null);
        return ingest(List.of(log), scope, true);
    }

    private int ingest(List<RuleExecutionLog> logs, ProjectAuthContext auth, boolean kafka) {
        List<RuleExecutionLog> scoped = new ArrayList<>();
        Map<String, RuleTraceRegistry> traces = new LinkedHashMap<>();
        Map<String, Map<String, RuleTraceRegistry>> trees = new LinkedHashMap<>();
        for (RuleExecutionLog log : logs) {
            if (log == null) continue;
            RuleDefinition rule = resolveRule(log.getRuleCode(), null, auth.getProjectId());
            if (!kafka && hasText(log.getProjectCode())
                    && !Objects.equals(log.getProjectCode(), auth.getProjectCode())) {
                throw invalid(403, "Project token does not match log project");
            }
            sanitize(log, auth);
            Map<String, RuleTraceRegistry> tree = new LinkedHashMap<>();
            try {
                collectTraces(log, rule, auth.getProjectId(), tree);
            } catch (ClientLogValidationException e) {
                throw e;
            } catch (com.alibaba.fastjson.JSONException | IllegalArgumentException | ClassCastException e) {
                throw invalid(400, "客户端 traceInfo 格式无效");
            }
            for (RuleTraceRegistry trace : tree.values()) {
                RuleTraceRegistry previous = traces.putIfAbsent(trace.getTraceId(), trace);
                if (previous != null && !RuleTraceRegistryService.sameIdentity(previous, trace)) {
                    throw invalid(409, "客户端 traceId 在同一批次中关联了不同资源");
                }
            }
            if (hasText(log.getTraceId())) {
                Map<String, RuleTraceRegistry> previous = trees.putIfAbsent(log.getTraceId(), tree);
                if (previous != null && !previous.keySet().equals(tree.keySet())) {
                    throw invalid(409, "同一客户端 traceId 的追踪树不能在重投时新增或删除节点");
                }
            }
            scoped.add(log);
        }
        // 先按一致顺序登记入口，重复入口只校验已存在子节点，不产生游离的追踪登记。
        Map<String, Boolean> inserted = new LinkedHashMap<>();
        trees.keySet().stream().sorted().forEach(id ->
                inserted.put(id, traceRegistryService.registerClientTrace(traces.get(id))));
        for (Map.Entry<String, Map<String, RuleTraceRegistry>> tree : trees.entrySet()) {
            boolean freshRoot = Boolean.TRUE.equals(inserted.get(tree.getKey()));
            tree.getValue().values().stream().filter(trace -> !trace.getTraceId().equals(tree.getKey()))
                    .sorted(Comparator.comparing(RuleTraceRegistry::getTraceId)).forEach(trace -> {
                        if (freshRoot) traceRegistryService.registerClientTrace(trace);
                        else traceRegistryService.validateClientTrace(trace);
                    });
        }
        List<RuleExecutionLog> fresh = new ArrayList<>();
        for (RuleExecutionLog log : scoped) {
            if (!hasText(log.getTraceId()) || Boolean.TRUE.equals(inserted.remove(log.getTraceId()))) fresh.add(log);
        }
        // 重投不覆盖首次接收的 HTTP 鉴权归因，也不再次计费。
        logService.saveLogicalBatch(fresh);
        for (RuleExecutionLog log : fresh) billingService.recordEngineExecutionLog(log, auth);
        return fresh.size();
    }

    private void sanitize(RuleExecutionLog log, ProjectAuthContext auth) {
        log.setId(null);
        log.setCreateTime(null);
        log.setProjectCode(auth.getProjectCode());
        log.setSource("CLIENT");
        log.setRootRuleId(null);
        log.setExecutionProjectId(null);
        log.setStartedAt(null);
        log.setHistoryFields(null);
        log.setAuthId(auth.getAuthId());
        log.setAuthCode(auth.getAuthCode());
        log.setAuthType(auth.getAuthType());
        log.setTokenId(auth.getTokenId());
        log.setTokenCode(auth.getTokenCode());
        log.setAuthPhase(auth.getAuthPhase());
        // 保留 SDK 实际执行的版本快照，仅缺失时沿用已有补全策略。
        logService.applyPublishedAttribution(log, auth.getProjectCode());
    }

    private RuleDefinition resolveRule(String ruleCode, Long ruleId, Long projectId) {
        if (!hasText(ruleCode) || !billingService.isRuleAccessible(projectId, ruleCode)) {
            throw invalid(403, "Rule is not available to authenticated project");
        }
        RuleDefinition rule = billingService.findDefinitionByRuleCode(ruleCode, projectId);
        if (rule == null || (ruleId != null && !ruleId.equals(rule.getId()))) {
            throw invalid(403, "Client trace rule identity does not match the available rule");
        }
        return rule;
    }

    private void collectTraces(RuleExecutionLog log, RuleDefinition rule, Long projectId,
                               Map<String, RuleTraceRegistry> traces) {
        if (!hasText(log.getTraceId())) return; // 兼容旧 HTTP；Kafka 必须提供幂等身份。
        validateRuleTrace(log.getTraceId(), projectId, rule);
        if (!hasText(log.getTraceInfo())) {
            collectTrace(log.getTraceId(), projectId, "RULE", rule.getId(), rule.getRuleCode(), null, traces);
            return;
        }
        JSONArray roots = JSON.parseArray(log.getTraceInfo());
        if (roots == null || roots.size() != 1 || !(roots.get(0) instanceof JSONObject)) {
            throw invalid(400, "客户端 traceInfo 必须包含一个入口 trace");
        }
        JSONObject root = roots.getJSONObject(0);
        if (!log.getTraceId().equals(root.getString("traceId"))
                || !log.getRuleCode().equals(root.getString("ruleCode"))) {
            throw invalid(400, "执行日志与入口追踪树身份不一致");
        }
        collectRuleTrace(root, projectId, null, traces, 0);
    }

    private void collectRuleTrace(JSONObject trace, Long projectId, String parentId,
                                  Map<String, RuleTraceRegistry> traces, int depth) {
        if (trace == null || depth > 128) throw invalid(400, "客户端追踪树结构无效或层级过深");
        String traceId = trace.getString("traceId");
        RuleDefinition rule = resolveRule(trace.getString("ruleCode"), trace.getLong("ruleId"), projectId);
        validateRuleTrace(traceId, projectId, rule);
        collectTrace(traceId, projectId, "RULE", rule.getId(), rule.getRuleCode(), parentId, traces);
        JSONArray events = trace.getJSONArray("events");
        if (events != null) {
            for (int i = 0; i < events.size(); i++) {
                JSONObject event = events.getJSONObject(i);
                if (event == null || !"MODULE_CALL".equals(event.getString("type"))) continue;
                String moduleId = event.getString("traceId");
                validateTraceScope(moduleId, projectId);
                String moduleType = event.getString("moduleType");
                if (!moduleId.startsWith(TraceIdGenerator.moduleTypeCode(moduleType))) {
                    throw invalid(400, "客户端模块 traceId 类型不匹配");
                }
                collectTrace(moduleId, projectId, moduleType, event.getLong("resourceId"),
                        event.getString("resourceCode"), traceId, traces);
            }
        }
        JSONArray children = trace.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                collectRuleTrace(children.getJSONObject(i), projectId, traceId, traces, depth + 1);
            }
        }
    }

    private void validateRuleTrace(String traceId, Long projectId, RuleDefinition rule) {
        validateTraceScope(traceId, projectId);
        if (!traceId.startsWith(TraceIdGenerator.ruleTypeCode(rule.getModelType()))
                || ("GLOBAL".equals(rule.getScope()) != "G".equals(traceId.substring(2, 3)))) {
            throw invalid(400, "客户端规则 traceId 类型或作用域不匹配");
        }
    }

    private void collectTrace(String traceId, Long projectId, String type, Long resourceId,
                              String code, String parentId, Map<String, RuleTraceRegistry> traces) {
        RuleTraceRegistry registry = new RuleTraceRegistry();
        registry.setTraceId(traceId);
        registry.setTraceType(traceId.substring(0, 2));
        registry.setScopeType(traceId.substring(2, 3));
        registry.setScopeCode(traceId.substring(3, 7));
        // GLOBAL 是资源作用域，调用项目仍需登记以防跨项目认领 trace。
        registry.setProjectId(projectId);
        registry.setResourceType(type);
        registry.setResourceId(resourceId);
        registry.setResourceCode(code);
        registry.setParentTraceId(parentId);
        RuleTraceRegistry previous = traces.putIfAbsent(traceId, registry);
        if (previous != null && !RuleTraceRegistryService.sameIdentity(previous, registry)) {
            throw invalid(400, "客户端 traceId 在同一批次中关联了不同资源");
        }
    }

    private void validateTraceScope(String traceId, Long projectId) {
        if (!TraceIdGenerator.isValid(traceId)) throw invalid(400, "客户端 traceId 格式无效");
        String code = traceId.substring(3, 7);
        if ("G".equals(traceId.substring(2, 3))) {
            if (!TraceIdGenerator.GLOBAL_SCOPE_CODE.equals(code)) throw invalid(400, "全局 traceId 作用域编码无效");
        } else if (!TraceIdGenerator.projectScopeCode(projectId).equals(code)) {
            throw invalid(403, "客户端 traceId 与认证项目不匹配");
        }
    }

    private static ClientLogValidationException invalid(int status, String message) {
        return new ClientLogValidationException(status, message);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

