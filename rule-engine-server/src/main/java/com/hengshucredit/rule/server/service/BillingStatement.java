package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class BillingStatement {
    final String project, projectCode, company, currency;
    final LocalDate begin, end;
    final Map<Long, RuleBillingConfig> products;
    final Section period = new Section();
    final Map<YearMonth, Section> months = new TreeMap<>(Comparator.reverseOrder());
    final Map<LocalDate, Section> days = new TreeMap<>(Comparator.reverseOrder());

    BillingStatement(String project, String projectCode, String company, String currency, LocalDate begin, LocalDate end,
                     Map<Long, RuleBillingConfig> products, List<RuleBillingSummary> rows) {
        this.project = required(project, "项目名称");
        this.projectCode = required(projectCode, "项目编码");
        this.company = required(company, "公司抬头");
        this.currency = required(currency, "币种");
        this.begin = begin;
        this.end = end;
        this.products = products;
        for (var row : rows) {
            required(products.get(row.getBillingConfigId()).getBillingName(), "计费名称");
            period.add(row);
            months.computeIfAbsent(YearMonth.from(row.getSummaryDate()), key -> new Section()).add(row);
            days.computeIfAbsent(row.getSummaryDate(), key -> new Section()).add(row);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("账单缺少" + field + "，请补齐后发送");
        return value;
    }

    static final class Totals {
        Long requests = 0L;
        long calls, charged;
        BigDecimal amount = BigDecimal.ZERO;
        private BigDecimal price;
        private boolean hasRows, multiplePrices;

        void add(RuleBillingSummary row) {
            if (row.getTotalCount() == null || row.getChargedCount() == null || row.getTotalAmount() == null)
                throw new IllegalArgumentException("计费汇总缺少次数或金额，请刷新对应日期后发送");
            if (row.getRequestCount() == null) requests = null;
            else if (requests != null) requests += row.getRequestCount();
            calls += row.getTotalCount();
            charged += row.getChargedCount();
            amount = amount.add(row.getTotalAmount());
            BigDecimal next = row.getBilledUnitPrice();
            if (next == null || (hasRows && price != null && price.compareTo(next) != 0)) multiplePrices = true;
            if (!hasRows) price = next;
            hasRows = true;
        }

        Object requestsValue() { return requests == null ? "未记录" : requests; }
        Object priceValue() { return multiplePrices ? "多档单价" : price; }
    }

    static final class Section {
        final Totals total = new Totals();
        final Map<Long, Totals> byProduct = new TreeMap<>();

        void add(RuleBillingSummary row) {
            total.add(row);
            byProduct.computeIfAbsent(row.getBillingConfigId(), key -> new Totals()).add(row);
        }
    }

    String title() {
        return (YearMonth.from(begin).equals(YearMonth.from(end)) ? YearMonth.from(begin).toString() : begin + " 至 " + end) + " 数据服务账单";
    }

    String introduction() {
        return "您好！随信附上" + project + "从 " + begin + " 到 " + end + " 已汇总的数据服务账单，"
                + "共 " + period.byProduct.size() + " 项产品，结算币种为 " + currency + "。请核对下列费用，逐日明细见附件。";
    }

    static String number(Object value) {
        return value instanceof BigDecimal decimal ? decimal.stripTrailingZeros().toPlainString() : String.valueOf(value);
    }

    String money(BigDecimal value) {
        return currency + " " + amountText(value);
    }

    private static String amountText(BigDecimal value) {
        return value.setScale(Math.max(2, value.stripTrailingZeros().scale())).toPlainString();
    }

    String plain() {
        StringBuilder out = new StringBuilder("尊敬的").append(project).append("团队：\n").append(introduction()).append('\n');
        appendText(out, "本期合计", period);
        months.forEach((month, section) -> appendText(out, month + " 月度合计", section));
        return out.append("\n").append(company).toString();
    }

    private void appendText(StringBuilder out, String title, Section section) {
        out.append('\n').append(title).append("　").append(money(section.total.amount)).append('\n');
        section.byProduct.forEach((id, totals) -> out.append(products.get(id).getBillingName()).append("　")
                .append(money(totals.amount)).append("（调用 ").append(totals.calls).append(" 次，计费 ")
                .append(totals.charged).append(" 次）\n"));
    }

    String tablesHtml() {
        StringBuilder html = new StringBuilder();
        appendTable(html, "本期账单合计", period);
        months.forEach((month, section) -> appendTable(html, month + " 月度账单", section));
        html.append("<p style='font-size:12px;color:#59647a'>请求次数按各产品的日汇总累加。</p>");
        if (period.total.requests == null)
            html.append("<p style='font-size:12px;color:#59647a'>部分明细缺少请求标识，对应请求次数标为“未记录”。</p>");
        return html.toString();
    }

    private void appendTable(StringBuilder out, String heading, Section section) {
        out.append("<h3 style='margin:20px 0 8px;font-size:15px'>").append(heading).append("　")
                .append(HtmlUtils.htmlEscape(money(section.total.amount))).append("</h3>")
                .append("<table width='100%' cellspacing='0' cellpadding='5' style='border-collapse:collapse;font-size:12px'>")
                .append("<thead style='background:#2934ea;color:white'><tr>");
        for (String label : List.of("计费名称", "请求次数", "调用次数", "计费次数", "计费单价", "账单费用"))
            out.append("<th scope='col'>").append(label).append("</th>");
        out.append("</tr></thead><tbody>");
        section.byProduct.forEach((id, totals) -> appendRow(out, products.get(id).getBillingName(), totals, false));
        appendRow(out, "合计", section.total, true);
        out.append("</tbody></table>");
    }

    private static void appendRow(StringBuilder out, String name, Totals totals, boolean total) {
        out.append(total ? "<tr style='background:#eff1ff;font-weight:bold'>" : "<tr>");
        for (Object field : List.of(name, totals.requestsValue(), totals.calls, totals.charged,
                total ? "按产品列示" : totals.priceValue(), amountText(totals.amount))) {
            out.append("<td style='text-align:center;border-bottom:1px solid #d9dfee'>")
                    .append(HtmlUtils.htmlEscape(number(field))).append("</td>");
        }
        out.append("</tr>");
    }
}
