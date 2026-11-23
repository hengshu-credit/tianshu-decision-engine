package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import com.hengshucredit.rule.server.consolelogin.ConsoleMailService;
import com.hengshucredit.rule.server.mapper.RuleBillingConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleBillingSummaryMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserProjectMapper;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import org.springframework.stereotype.Service;

import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.server.system.SystemBrandAssetService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
public class BillingStatementService {
    private final RuleBillingSummaryMapper summaries;
    private final RuleBillingConfigMapper configs;
    private final ConsoleUserProjectMapper projects;
    private final ConsoleProjectAccess access;
    private final ConsoleMailService mail;

    private final SystemBrandAssetService assets;

    public BillingStatementService(RuleBillingSummaryMapper summaries, RuleBillingConfigMapper configs,
                                  ConsoleUserProjectMapper projects, ConsoleProjectAccess access, ConsoleMailService mail,
                                   SystemBrandAssetService assets) {
        this.summaries = summaries;
        this.configs = configs;
        this.projects = projects;
        this.access = access;
        this.mail = mail;
        this.assets = assets;
    }

    public void send(long id, LocalDate begin, LocalDate end) {
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
        if (begin == null && end == null) {
            var month = YearMonth.from(row.getSummaryDate());
            begin = month.atDay(1);
            end = month.atEndOfMonth();
        }
        if (begin == null || end == null || begin.isAfter(row.getSummaryDate()) || end.isBefore(row.getSummaryDate()))
            throw new IllegalArgumentException("账期必须包含所选汇总日期，请重新查询");

        var brand = assets.statementBrand();
        String company = companyName(config, brand.name());
        var eligible = configs.selectList(new LambdaQueryWrapper<RuleBillingConfig>()
                .eq(RuleBillingConfig::getStatus, 1).eq(RuleBillingConfig::getBillEmail, config.getBillEmail())
                .and(q -> q.eq(RuleBillingConfig::getScope, "GLOBAL").or().eq(RuleBillingConfig::getProjectId, row.getProjectId())))
                .stream().filter(item -> config.getBillEmail().equals(item.getBillEmail())
                        && company.equals(companyName(item, brand.name())))
                .collect(Collectors.toMap(RuleBillingConfig::getId, Function.identity()));
        if (!eligible.containsKey(config.getId())) throw new IllegalArgumentException("计费配置已更新，请重新查询");
        var query = new LambdaQueryWrapper<RuleBillingSummary>()
                .eq(RuleBillingSummary::getProjectId, row.getProjectId())
                .in(RuleBillingSummary::getBillingConfigId, eligible.keySet())
                .eq(RuleBillingSummary::getCurrency, row.getCurrency())
                .between(RuleBillingSummary::getSummaryDate, begin, end);
        if (row.getAuthId() == null) query.isNull(RuleBillingSummary::getAuthId);
        else query.eq(RuleBillingSummary::getAuthId, row.getAuthId());
        if (row.getActorUserId() == null) query.isNull(RuleBillingSummary::getActorUserId);
        else query.eq(RuleBillingSummary::getActorUserId, row.getActorUserId());
        var days = summaries.selectList(query).stream().filter(day -> row.getCurrency().equals(day.getCurrency()))
                .toList();
        if (days.stream().noneMatch(day -> Objects.equals(day.getId(), id)))
            throw new IllegalArgumentException("汇总已刷新，请重新查询后发送");
        var project = projects.browseOption(row.getProjectId());
        if (project == null) throw new IllegalArgumentException("所属项目不存在或不可访问，无法填写账单项目资料");
        String code = project.projectCode();
        String name = project.projectName();
        var bill = new BillingStatement(name, code, company, row.getCurrency(), begin, end, eligible, days);
        mail.sendStatement(brand, config.getBillEmail(), bill.title(), name + "团队", bill.introduction(), bill.plain(),
                bill.tablesHtml(), company, begin + "至" + end + "-账单.xlsx", BillingStatementWorkbook.render(bill, brand.logo()));
    }

    private static String companyName(RuleBillingConfig config, String defaultName) {
        String name = config.getBillCompanyName();
        return name == null || name.isBlank() ? defaultName : name.strip();
    }
}
