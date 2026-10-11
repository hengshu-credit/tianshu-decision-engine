package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.dto.ExecutionVersionQuery;
import com.hengshucredit.rule.model.entity.RuleBillingRecord;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import com.hengshucredit.rule.server.mapper.RuleBillingVersionSummaryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDate;

@Service
public class RuleBillingVersionSummaryService {
    @Resource private RuleBillingVersionSummaryMapper mapper;
    @Resource private ExecutionVersionFilter versions;
    @Resource private ProjectFilterService projects;

    public static boolean selected(ExecutionVersionQuery query) {
        return query != null && (query.getRuleId() != null || query.getRevisionId() != null
                || query.getVersionMode() != null && !"ALL".equals(query.getVersionMode()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public IPage<RuleBillingSummary> page(int pageNum, int pageSize, String billingTarget, String billingCode,
                                         String projectCode, String projectName, String authType, String authCode,
                                         String beginDate, String endDate, ExecutionVersionQuery query) {
        Integer number = versions.resolve(query);
        var matches = projects.resolve(null, projectName);
        if (matches.isActive() && matches.isEmpty()) return new Page<>(pageNum, pageSize);
        var filter = new LambdaQueryWrapper<RuleBillingRecord>();
        filter.eq(query.getRuleId() != null, RuleBillingRecord::getRuleId, query.getRuleId())
                .eq(number != null, RuleBillingRecord::getRuleVersion, number)
                .eq(query.getRevisionId() != null, RuleBillingRecord::getRevisionId, query.getRevisionId())
                .eq(StringUtils.hasText(billingTarget), RuleBillingRecord::getBillingTarget, billingTarget)
                .like(StringUtils.hasText(billingCode), RuleBillingRecord::getBillingCode, billingCode)
                .like(StringUtils.hasText(projectCode), RuleBillingRecord::getProjectCode, projectCode)
                .in(matches.isActive(), RuleBillingRecord::getProjectCode, matches.getProjectCodes())
                .eq(StringUtils.hasText(authType), RuleBillingRecord::getAuthType, authType)
                .like(StringUtils.hasText(authCode), RuleBillingRecord::getAuthCode, authCode);
        if (StringUtils.hasText(beginDate)) filter.ge(RuleBillingRecord::getOccurTime, LocalDate.parse(beginDate).atStartOfDay());
        if (StringUtils.hasText(endDate)) filter.lt(RuleBillingRecord::getOccurTime, LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        return mapper.selectVersionSummary(new Page<>(pageNum, pageSize), filter);
    }
}
