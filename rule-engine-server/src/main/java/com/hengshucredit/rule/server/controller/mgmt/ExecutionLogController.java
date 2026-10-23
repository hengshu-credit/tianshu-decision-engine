package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.server.service.RuleExecutionLogService;
import com.hengshucredit.rule.server.service.RuleExecutionReplayService;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/log")
public class ExecutionLogController {
    @Resource
    private RuleExecutionLogService logService;
    @Resource
    private RuleExecutionReplayService replayService;

    @GetMapping("/list")
    public ApiResponse<IPage<RuleExecutionLog>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String authType,
            @RequestParam(required = false) String authCode,
            @RequestParam(required = false) String tokenCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @ModelAttribute com.hengshucredit.rule.model.dto.ExecutionVersionQuery versionQuery) {
        return ApiResponse.ok(logService.pageList(pageNum, pageSize, modelType, source, projectCode, projectName,
                ruleCode, traceId, authType, authCode, tokenCode, startTime, endTime, versionQuery));
    }

    @GetMapping("/rule-set-stats")
    public ApiResponse<Map<String, Object>> ruleSetStats(
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ApiResponse.ok(logService.ruleSetStats(projectCode, projectName, ruleCode, startTime, endTime));
    }

    /** 仅基于历史日志离线回溯；不会写入新的执行日志、来源日志或计费记录。 */
    @PostMapping("/{id}/replay")
    @RequirePermission("log:view")
    public ApiResponse<Map<String, Object>> replay(@PathVariable Long id) {
        return ApiResponse.ok(replayService.replay(id));
    }
}
