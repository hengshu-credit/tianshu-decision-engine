package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleBillingService;
import com.hengshucredit.rule.server.service.RuleExecutionLogService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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
            String projectCode = request.getAttribute("projectCode") == null
                    ? null
                    : String.valueOf(request.getAttribute("projectCode"));
            if (projectCode == null || projectCode.trim().isEmpty()) {
                return R.fail(401, "Unauthorized project token");
            }
            for (RuleExecutionLog log : logs) {
                if (log == null) {
                    continue;
                }
                if (log.getProjectCode() != null && !log.getProjectCode().isEmpty()
                        && !projectCode.equals(log.getProjectCode())) {
                    return R.fail(403, "Project token does not match log project");
                }
                log.setProjectCode(projectCode);
                if (log.getSource() == null || log.getSource().trim().isEmpty()) {
                    log.setSource("CLIENT");
                }
            }
            logService.saveBatch(logs);
            for (RuleExecutionLog log : logs) {
                billingService.recordEngineExecutionLog(log);
            }
        }
        return R.ok();
    }
}
