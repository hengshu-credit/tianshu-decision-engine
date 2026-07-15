package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.core.trace.TraceIdGenerator;
import com.hengshucredit.rule.model.entity.RuleProject;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RuntimeTraceService {

    @Resource
    private RuleTraceRegistryService traceRegistryService;

    @Resource
    private RuleProjectService projectService;

    public ModuleTrace startModule(String moduleType, Long projectId,
                                   Long resourceId, String resourceCode) {
        boolean global = projectId == null || projectId <= 0;
        String scopeType = global ? "G" : "P";
        String scopeCode = global
                ? TraceIdGenerator.GLOBAL_SCOPE_CODE : resolveProjectScopeCode(projectId);
        String typeCode = TraceIdGenerator.moduleTypeCode(moduleType);
        Object currentRuleTrace = RuntimeContextBridge.currentRule().get("traceId");
        String ruleTraceId = currentRuleTrace == null ? null : String.valueOf(currentRuleTrace);
        String traceId = traceRegistryService == null
                ? TraceIdGenerator.generate(typeCode, scopeType, scopeCode)
                : traceRegistryService.allocate(typeCode, scopeType, scopeCode, projectId,
                        moduleType, resourceId, resourceCode, ruleTraceId);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "MODULE_CALL");
        event.put("traceId", traceId);
        event.put("ruleTraceId", ruleTraceId);
        event.put("moduleType", moduleType);
        event.put("resourceId", resourceId);
        event.put("resourceCode", resourceCode);
        event.put("status", "RUNNING");
        RuntimeContextBridge.addTraceEvent(event);
        return new ModuleTrace(traceId, ruleTraceId, System.currentTimeMillis(), event);
    }

    public void completeModule(ModuleTrace trace, boolean success,
                               String errorMessage, Long durationMs) {
        if (trace == null) {
            return;
        }
        trace.event.put("status", success ? "SUCCESS" : "FAILED");
        trace.event.put("durationMs", durationMs == null
                ? System.currentTimeMillis() - trace.startTime : durationMs);
        if (errorMessage != null) {
            trace.event.put("errorMessage", errorMessage);
        }
    }

    private String resolveProjectScopeCode(Long projectId) {
        if (projectService != null) {
            RuleProject project = projectService.getById(projectId);
            if (project != null && hasText(project.getTraceScopeCode())) {
                return project.getTraceScopeCode();
            }
        }
        return TraceIdGenerator.projectScopeCode(projectId);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static class ModuleTrace {
        private final String traceId;
        private final String ruleTraceId;
        private final long startTime;
        private final Map<String, Object> event;

        private ModuleTrace(String traceId, String ruleTraceId, long startTime,
                            Map<String, Object> event) {
            this.traceId = traceId;
            this.ruleTraceId = ruleTraceId;
            this.startTime = startTime;
            this.event = event;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getRuleTraceId() {
            return ruleTraceId;
        }
    }
}
