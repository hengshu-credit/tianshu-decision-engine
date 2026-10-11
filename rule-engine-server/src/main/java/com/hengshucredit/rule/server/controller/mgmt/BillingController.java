package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleBillingRecord;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import com.hengshucredit.rule.model.entity.RuleBillingSummaryTask;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.governance.GovernedProjectionMutation;
import com.hengshucredit.rule.server.service.RuleBillingService;
import com.hengshucredit.rule.server.service.BillingSummaryTaskService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/billing")
public class BillingController {

    @Resource
    private RuleBillingService billingService;

    @Resource
    private BillingSummaryTaskService summaryTasks;

    @Resource private com.hengshucredit.rule.server.service.RuleBillingVersionSummaryService versionSummaries;

    @GetMapping("/config/list")
    public R<IPage<RuleBillingConfig>> listConfigs(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String billingTarget,
            @RequestParam(required = false) String billingCode,
            @RequestParam(required = false) Integer status) {
        return R.ok(billingService.pageConfigs(pageNum, pageSize, scope, projectId, projectCode, projectName,
                billingTarget, billingCode, status));
    }

    @PostMapping("/config")
    @GovernedProjectionMutation
    public R<Void> createConfig(@RequestBody RuleBillingConfig config) {
        billingService.saveConfigWithDefaults(config);
        return R.ok();
    }

    @PutMapping("/config")
    @GovernedProjectionMutation
    public R<Void> updateConfig(@RequestBody RuleBillingConfig config) {
        billingService.updateConfigWithDefaults(config);
        return R.ok();
    }

    @DeleteMapping("/config/{id:\\d+}")
    @GovernedProjectionMutation
    public R<Void> deleteConfig(@PathVariable Long id) {
        billingService.removeById(id);
        return R.ok();
    }

    @GetMapping("/record/list")
    public R<IPage<RuleBillingRecord>> listRecords(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String billingTarget,
            @RequestParam(required = false) String billingCode,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String authType,
            @RequestParam(required = false) String authCode,
            @RequestParam(required = false) String tokenCode,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime, @ModelAttribute com.hengshucredit.rule.model.dto.ExecutionVersionQuery versionQuery) {
        return R.ok(billingService.pageRecords(pageNum, pageSize, billingTarget, billingCode, projectCode, projectName,
                authType, authCode, tokenCode, beginTime, endTime, versionQuery));
    }

    @GetMapping("/summary/list")
    public R<IPage<RuleBillingSummary>> listSummaries(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String billingTarget,
            @RequestParam(required = false) String billingCode,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String authType,
            @RequestParam(required = false) String authCode,
            @RequestParam(required = false) String beginDate,
            @RequestParam(required = false) String endDate, @ModelAttribute com.hengshucredit.rule.model.dto.ExecutionVersionQuery versionQuery) {
        if (com.hengshucredit.rule.server.service.RuleBillingVersionSummaryService.selected(versionQuery))
            return R.ok(versionSummaries.page(pageNum, pageSize, billingTarget, billingCode, projectCode, projectName,
                    authType, authCode, beginDate, endDate, versionQuery));
        return R.ok(billingService.pageSummaries(pageNum, pageSize, billingTarget, billingCode, projectCode, projectName,
                authType, authCode, beginDate, endDate));
    }

    @PostMapping("/summary/refresh")
    public R<RuleBillingSummaryTask> refreshSummary(@RequestBody(required = false) Map<String, String> body) {
        String dateText = body == null ? null : body.get("summaryDate");
        LocalDate date = dateText == null || dateText.trim().isEmpty() ? LocalDate.now() : LocalDate.parse(dateText);
        return R.ok(summaryTasks.submit(date));
    }

    @GetMapping("/summary/refresh/{taskId}")
    public R<RuleBillingSummaryTask> getSummaryTask(@PathVariable String taskId) {
        return R.ok(summaryTasks.get(taskId));
    }

    @GetMapping("/summary/refresh")
    public R<RuleBillingSummaryTask> latestSummaryTask(@RequestParam(required = false) String summaryDate) {
        LocalDate date = summaryDate == null || summaryDate.isBlank() ? LocalDate.now() : LocalDate.parse(summaryDate);
        return R.ok(summaryTasks.latest(date));
    }
}
