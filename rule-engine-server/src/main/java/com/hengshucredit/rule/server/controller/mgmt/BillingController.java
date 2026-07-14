package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleBillingRecord;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleBillingService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/billing")
public class BillingController {

    @Resource
    private RuleBillingService billingService;

    @GetMapping("/config/list")
    public R<IPage<RuleBillingConfig>> listConfigs(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String billingTarget,
            @RequestParam(required = false) String billingCode,
            @RequestParam(required = false) Integer status) {
        return R.ok(billingService.pageConfigs(pageNum, pageSize, scope, projectId, billingTarget, billingCode, status));
    }

    @PostMapping("/config")
    public R<Void> createConfig(@RequestBody RuleBillingConfig config) {
        billingService.saveConfigWithDefaults(config);
        return R.ok();
    }

    @PutMapping("/config")
    public R<Void> updateConfig(@RequestBody RuleBillingConfig config) {
        billingService.updateConfigWithDefaults(config);
        return R.ok();
    }

    @DeleteMapping("/config/{id:\\d+}")
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
            @RequestParam(required = false) String authType,
            @RequestParam(required = false) String authCode,
            @RequestParam(required = false) String tokenCode,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime) {
        return R.ok(billingService.pageRecords(pageNum, pageSize, billingTarget, billingCode, projectCode,
                authType, authCode, tokenCode, beginTime, endTime));
    }

    @GetMapping("/summary/list")
    public R<IPage<RuleBillingSummary>> listSummaries(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String billingTarget,
            @RequestParam(required = false) String billingCode,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String authType,
            @RequestParam(required = false) String authCode,
            @RequestParam(required = false) String beginDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(billingService.pageSummaries(pageNum, pageSize, billingTarget, billingCode, projectCode,
                authType, authCode, beginDate, endDate));
    }

    @PostMapping("/summary/refresh")
    public R<Map<String, Object>> refreshSummary(@RequestBody(required = false) Map<String, String> body) {
        String dateText = body == null ? null : body.get("summaryDate");
        LocalDate date = dateText == null || dateText.trim().isEmpty() ? LocalDate.now() : LocalDate.parse(dateText);
        int count = billingService.refreshSummary(date);
        Map<String, Object> result = new HashMap<>();
        result.put("summaryDate", date.toString());
        result.put("summaryCount", count);
        return R.ok(result);
    }
}
