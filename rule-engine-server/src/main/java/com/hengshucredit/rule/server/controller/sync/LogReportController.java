package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.ClientExecutionLogIngestionService;
import com.hengshucredit.rule.server.service.ClientLogValidationException;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/rule/log")
public class LogReportController {
    @Resource
    private ClientExecutionLogIngestionService ingestionService;

    @PostMapping("/report")
    public R<Void> report(@RequestBody List<RuleExecutionLog> logs, HttpServletRequest request) {
        try {
            ingestionService.ingestHttp(logs, ProjectAuthContext.from(request));
            return R.ok();
        } catch (ClientLogValidationException e) {
            // 服务事务回滚后再转换响应，避免部分 trace 被提前提交。
            return R.fail(e.getStatusCode(), e.getMessage());
        }
    }
}
