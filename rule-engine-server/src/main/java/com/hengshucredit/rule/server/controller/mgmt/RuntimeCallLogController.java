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

import javax.annotation.Resource;
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
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return R.ok(logService.pageList(pageNum, pageSize, moduleType, actionType,
                targetCode, traceId, success, startTime, endTime));
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
