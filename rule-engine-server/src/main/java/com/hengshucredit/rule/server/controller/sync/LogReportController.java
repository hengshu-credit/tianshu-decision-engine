package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleBillingService;
import com.hengshucredit.rule.server.service.RuleExecutionLogService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/rule/log")
public class LogReportController {

    @Resource
    private RuleExecutionLogService logService;

    @Resource
    private RuleBillingService billingService;

    @PostMapping("/report")
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
                if (log.getSource() == null || log.getSource().trim().isEmpty()) {
                    log.setSource("CLIENT");
                }
                scopedLogs.add(log);
            }
            logService.saveBatch(scopedLogs);
            for (RuleExecutionLog log : scopedLogs) {
                billingService.recordEngineExecutionLog(log, authContext);
            }
        }
        return R.ok();
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
