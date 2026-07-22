package com.hengshucredit.rule.server.controller.sync;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.trace.TraceIdGenerator;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleTraceRegistry;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleBillingService;
import com.hengshucredit.rule.server.service.RuleExecutionLogService;
import com.hengshucredit.rule.server.service.RuleTraceRegistryService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/rule/log")
public class LogReportController {

    @Resource
    private RuleExecutionLogService logService;

    @Resource
    private RuleBillingService billingService;

    @Resource
    private RuleTraceRegistryService traceRegistryService;

    @PostMapping("/report")
    @Transactional
    public R<Void> report(@RequestBody List<RuleExecutionLog> logs, HttpServletRequest request) {
        if (logs != null && !logs.isEmpty()) {
            ProjectAuthContext authContext = ProjectAuthContext.from(request);
            if (authContext == null || authContext.getProjectId() == null) {
                return R.fail(401, "Unauthorized project token");
            }
            String projectCode = authContext.getProjectCode();
            List<RuleExecutionLog> scopedLogs = new ArrayList<>();
            for (RuleExecutionLog log : logs) {
                if (log == null) {
                    continue;
                }
                if (log.getRuleCode() == null || log.getRuleCode().trim().isEmpty()
                        || !billingService.isRuleAccessible(authContext.getProjectId(), log.getRuleCode())) {
                    return R.fail(403, "Rule is not available to authenticated project");
                }
                if (log.getProjectCode() != null && !log.getProjectCode().isEmpty()
                        && !log.getProjectCode().equals(projectCode)) {
                    return R.fail(403, "Project token does not match log project");
                }
                log.setProjectCode(projectCode);
                applyAuthAttribution(log, authContext);
                logService.applyPublishedAttribution(log, projectCode);
                if (log.getSource() == null || log.getSource().trim().isEmpty()) {
                    log.setSource("CLIENT");
                }
                scopedLogs.add(log);
            }
            try {
                for (RuleExecutionLog log : scopedLogs) {
                    registerClientTraces(log, authContext.getProjectId());
                }
            } catch (IllegalArgumentException e) {
                markTransactionRollbackOnly();
                return R.fail(400, e.getMessage());
            }
            logService.saveBatch(scopedLogs);
            for (RuleExecutionLog log : scopedLogs) {
                billingService.recordEngineExecutionLog(log, authContext);
            }
        }
        return R.ok();
    }

    private void registerClientTraces(RuleExecutionLog log, Long projectId) {
        if (log == null || !hasText(log.getTraceId())) {
            return;
        }
        validateTraceScope(log.getTraceId(), projectId);
        if (!hasText(log.getTraceInfo())) {
            registerTrace(log.getTraceId(), projectId, "RULE", null,
                    log.getRuleCode(), null);
            return;
        }
        JSONArray roots;
        try {
            roots = JSON.parseArray(log.getTraceInfo());
        } catch (Exception e) {
            throw new IllegalArgumentException("客户端 traceInfo 格式无效", e);
        }
        if (roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("客户端 traceInfo 缺少入口 trace");
        }
        JSONObject root = roots.getJSONObject(0);
        if (root == null || !log.getTraceId().equals(root.getString("traceId"))) {
            throw new IllegalArgumentException("执行日志 traceId 与入口追踪树不一致");
        }
        registerRuleTraceTree(root, projectId, null);
    }

    private void registerRuleTraceTree(JSONObject trace, Long projectId, String parentTraceId) {
        if (trace == null) {
            return;
        }
        String traceId = trace.getString("traceId");
        validateTraceScope(traceId, projectId);
        registerTrace(traceId, projectId, "RULE", trace.getLong("ruleId"),
                trace.getString("ruleCode"), parentTraceId);

        JSONArray events = trace.getJSONArray("events");
        if (events != null) {
            for (int i = 0; i < events.size(); i++) {
                JSONObject event = events.getJSONObject(i);
                if (event == null || !"MODULE_CALL".equals(event.getString("type"))) {
                    continue;
                }
                String moduleTraceId = event.getString("traceId");
                validateTraceScope(moduleTraceId, projectId);
                registerTrace(moduleTraceId, projectId, event.getString("moduleType"),
                        event.getLong("resourceId"), event.getString("resourceCode"), traceId);
            }
        }

        JSONArray children = trace.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                registerRuleTraceTree(children.getJSONObject(i), projectId, traceId);
            }
        }
    }

    private void registerTrace(String traceId, Long authenticatedProjectId,
                               String resourceType, Long resourceId,
                               String resourceCode, String parentTraceId) {
        RuleTraceRegistry registry = new RuleTraceRegistry();
        registry.setTraceId(traceId);
        registry.setTraceType(traceId.substring(0, 2));
        registry.setScopeType(traceId.substring(2, 3));
        registry.setScopeCode(traceId.substring(3, 7));
        registry.setProjectId("P".equals(registry.getScopeType()) ? authenticatedProjectId : null);
        registry.setResourceType(resourceType);
        registry.setResourceId(resourceId);
        registry.setResourceCode(resourceCode);
        registry.setParentTraceId(parentTraceId);
        traceRegistryService.registerExisting(registry);
    }

    private void validateTraceScope(String traceId, Long projectId) {
        if (!TraceIdGenerator.isValid(traceId)) {
            throw new IllegalArgumentException("客户端 traceId 格式无效");
        }
        String scopeType = traceId.substring(2, 3);
        String scopeCode = traceId.substring(3, 7);
        if ("G".equals(scopeType)) {
            if (!TraceIdGenerator.GLOBAL_SCOPE_CODE.equals(scopeCode)) {
                throw new IllegalArgumentException("全局 traceId 作用域编码无效");
            }
            return;
        }
        if (!TraceIdGenerator.projectScopeCode(projectId).equals(scopeCode)) {
            throw new IllegalArgumentException("客户端 traceId 与认证项目不匹配");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void markTransactionRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (RuntimeException ignored) {
            // 直接调用控制器的单元测试没有事务代理，无需回滚。
        }
    }

    private void applyAuthAttribution(RuleExecutionLog log, ProjectAuthContext authContext) {
        log.setAuthId(authContext == null ? null : authContext.getAuthId());
        log.setAuthCode(authContext == null ? null : authContext.getAuthCode());
        log.setAuthType(authContext == null ? null : authContext.getAuthType());
        log.setTokenId(authContext == null ? null : authContext.getTokenId());
        log.setTokenCode(authContext == null ? null : authContext.getTokenCode());
        log.setAuthPhase(authContext == null ? null : authContext.getAuthPhase());
    }
}
