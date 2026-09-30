package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleRuntimeCallLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/runtime-log")
public class RuntimeCallLogController {

    @Resource
    private RuleRuntimeCallLogService logService;

    @GetMapping("/list")
    public R<IPage<RuleRuntimeCallLog>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String moduleType,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String targetCode,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String callId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        IPage<RuleRuntimeCallLog> page = logService.pageList(pageNum, pageSize, moduleType, actionType,
                targetCode, traceId, callId, projectId, projectCode, success, startTime, endTime);
        if (page.getRecords() != null) {
            page.getRecords().forEach(item -> {
                item.setRawRequestBody(null);
                item.setRawResponseBody(null);
                item.setOriginalRequestBody(null);
                item.setOriginalResponseBody(null);
                item.setTraceSteps(null);
            });
        }
        return R.ok(page);
    }

    @GetMapping("/{id}/payload")
    public R<Map<String, Object>> payload(@org.springframework.web.bind.annotation.PathVariable Long id,
                                           @RequestParam(required = false) Long projectId) {
        return R.ok(logService.payload(id, projectId));
    }

    @GetMapping("/payload/by-call-id")
    public R<Map<String, Object>> payloadByCallId(@RequestParam String callId,
                                                   @RequestParam(required = false) Long projectId) {
        return R.ok(logService.payloadByCallId(callId, projectId));
    }

    @GetMapping("/payload/by-root-trace-id")
    public R<java.util.List<Map<String, Object>>> payloadsByRootTraceId(
            @RequestParam String rootTraceId,
            @RequestParam(required = false) Long projectId) {
        return R.ok(logService.payloadsByRootTraceId(rootTraceId, projectId));
    }

    @GetMapping("/external-api-stats")
    public R<Map<String, Object>> externalApiStats(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long targetRefId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return R.ok(logService.externalApiStats(projectId, targetRefId, startTime, endTime));
    }
}
