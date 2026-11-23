package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import com.hengshucredit.rule.server.consolelogin.ConsoleMailService;
import com.hengshucredit.rule.server.mapper.RuleBillingConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleBillingSummaryMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.Objects;

@Service
public class BillingStatementService {
    private final RuleBillingSummaryMapper summaries;
    private final RuleBillingConfigMapper configs;
    private final RuleProjectMapper projects;
    private final ConsoleProjectAccess access;
    private final ConsoleMailService mail;

    public BillingStatementService(RuleBillingSummaryMapper summaries, RuleBillingConfigMapper configs,
                                  RuleProjectMapper projects, ConsoleProjectAccess access, ConsoleMailService mail) {
        this.summaries = summaries;
        this.configs = configs;
        this.projects = projects;
        this.access = access;
        this.mail = mail;
    }

    public void send(long id) {
        // 两次汇总读取均经过控制台记录归属过滤。客户端只提交汇总 ID。
        var row = summaries.selectById(id);
        if (row == null) throw new IllegalArgumentException("汇总已刷新或不可访问，请重新查询");
        access.requirePermission("billing:edit", row.getProjectId());
        if (row.getBillingConfigId() == null || row.getProjectId() == null)
            throw new IllegalArgumentException("这条汇总缺少可确认的项目或计费配置，请刷新对应日期后重试");
        var config = configs.selectById(row.getBillingConfigId());
        if (config == null || !Objects.equals(config.getStatus(), 1) ||
                !("GLOBAL".equals(config.getScope()) || Objects.equals(row.getProjectId(), config.getProjectId())) ||
                !ConsoleMailService.validBillRecipient(config.getBillEmail()))
            throw new IllegalArgumentException("请先启用计费配置，并审批生效账单收件邮箱");
        var month = YearMonth.from(row.getSummaryDate());
        var query = new LambdaQueryWrapper<RuleBillingSummary>()
                .eq(RuleBillingSummary::getProjectId, row.getProjectId())
                .eq(RuleBillingSummary::getBillingConfigId, config.getId())
                .eq(RuleBillingSummary::getBillingTarget, row.getBillingTarget())
                .eq(RuleBillingSummary::getCurrency, row.getCurrency())
                .between(RuleBillingSummary::getSummaryDate, month.atDay(1), month.atEndOfMonth())
                .orderByDesc(RuleBillingSummary::getSummaryDate);
        if (row.getAuthId() == null) query.isNull(RuleBillingSummary::getAuthId);
        else query.eq(RuleBillingSummary::getAuthId, row.getAuthId());
        if (row.getActorUserId() == null) query.isNull(RuleBillingSummary::getActorUserId);
        else query.eq(RuleBillingSummary::getActorUserId, row.getActorUserId());
        if (row.getTargetRefId() == null) query.isNull(RuleBillingSummary::getTargetRefId);
        else query.eq(RuleBillingSummary::getTargetRefId, row.getTargetRefId());
        var days = summaries.selectList(query).stream().filter(day -> Objects.equals(row.getCurrency(), day.getCurrency())).toList();
        if (days.stream().noneMatch(day -> Objects.equals(day.getId(), id)))
            throw new IllegalArgumentException("汇总已刷新，请重新查询后发送");
        var project = projects.selectById(row.getProjectId());
        String name = project == null ? row.getProjectCode() : project.getProjectName();
        if (name == null || name.isBlank()) name = "项目 " + row.getProjectId();
        var statement = new BillingStatement(config, name, row, days);
        mail.sendStatement(config.getBillEmail(), statement.title(), statement.plain(), statement.html(),
                month + "-账单-" + config.getId() + ".xlsx", statement.workbook());
    }
}
