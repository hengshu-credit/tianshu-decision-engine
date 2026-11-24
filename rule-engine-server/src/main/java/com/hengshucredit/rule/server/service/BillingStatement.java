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
    final String project, projectCode, company;
    final LocalDate begin, end;
    final Map<Long, RuleBillingConfig> products;
    final Map<String, CurrencyBill> currencies = new TreeMap<>();

    BillingStatement(String project, String projectCode, String company, LocalDate begin, LocalDate end,
                     Map<Long, RuleBillingConfig> products, List<RuleBillingSummary> rows) {
        this.project = required(project, "项目名称");
        this.projectCode = required(projectCode, "项目编码");
        this.company = required(company, "公司抬头");
        this.begin = begin;
        this.end = end;
        this.products = products;
        for (var row : rows) {
            required(products.get(row.getBillingConfigId()).getBillingName(), "计费名称");
            currencies.computeIfAbsent(required(row.getCurrency(), "币种"), key -> new CurrencyBill()).add(row);
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
        private BigDecimal lowest, highest;
        private boolean missingPrice;

        void add(RuleBillingSummary row) {
            if (row.getTotalCount() == null || row.getChargedCount() == null || row.getTotalAmount() == null)
                throw new IllegalArgumentException("计费汇总缺少次数或金额，请刷新对应日期后发送");
            if (row.getRequestCount() == null) requests = null;
            else if (requests != null) requests += row.getRequestCount();
            calls += row.getTotalCount();
            charged += row.getChargedCount();
            amount = amount.add(row.getTotalAmount());
            BigDecimal min = row.getMinUnitPrice() == null ? row.getBilledUnitPrice() : row.getMinUnitPrice();
            BigDecimal max = row.getMaxUnitPrice() == null ? row.getBilledUnitPrice() : row.getMaxUnitPrice();
            if (min == null || max == null) {
                missingPrice = true;
            } else {
                lowest = lowest == null ? min : lowest.min(min);
                highest = highest == null ? max : highest.max(max);
            }
        }

        Object requestsValue() { return requests == null ? "未记录" : requests; }
        Object priceValue() {
            if (missingPrice) return "历史单价未记录";
            return lowest.compareTo(highest) == 0 ? lowest : number(lowest) + "–" + number(highest);
        }
    }

    static final class CurrencyBill {
        final Section period = new Section();
        final Map<YearMonth, Section> months = new TreeMap<>(Comparator.reverseOrder());
        final Map<LocalDate, Section> days = new TreeMap<>(Comparator.reverseOrder());

        void add(RuleBillingSummary row) {
            period.add(row);
            months.computeIfAbsent(YearMonth.from(row.getSummaryDate()), key -> new Section()).add(row);
            days.computeIfAbsent(row.getSummaryDate(), key -> new Section()).add(row);
        }
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
                + "共 " + currencies.values().stream().flatMap(s -> s.period.byProduct.keySet().stream()).distinct().count()
                + " 项产品，结算币种为 " + String.join("、", currencies.keySet()) + "。请核对下列费用，逐日明细见附件。";
    }

    static String number(Object value) {
        return value instanceof BigDecimal decimal ? decimal.stripTrailingZeros().toPlainString() : String.valueOf(value);
    }

    static String money(String currency, BigDecimal value) {
        return currency + " " + amountText(value);
    }

    private static String amountText(BigDecimal value) {
        return value.setScale(Math.max(2, value.stripTrailingZeros().scale())).toPlainString();
    }

    String plain() {
        StringBuilder out = new StringBuilder("尊敬的").append(project).append("团队：\n").append(introduction()).append('\n');
        currencies.forEach((unit, bill) -> {
            out.append('\n').append(unit).append(" 账单\n本期合计　").append(money(unit, bill.period.total.amount)).append('\n');
            bill.months.forEach((month, section) -> {
                out.append("  ").append(month).append(" 月度合计　").append(money(unit, section.total.amount)).append('\n');
                section.byProduct.forEach((id, line) -> out.append("    ").append(products.get(id).getBillingName())
                        .append("　").append(money(unit, line.amount)).append("（调用 ").append(line.calls)
                        .append(" 次，计费 ").append(line.charged).append(" 次，单价 ").append(number(line.priceValue())).append("）\n"));
            });
        });
        return out.toString();
    }

    String tablesHtml() {
        StringBuilder out = new StringBuilder();
        currencies.forEach((unit, bill) -> {
            out.append("<h3 style='margin:20px 0 8px;font-size:13px'>").append(HtmlUtils.htmlEscape(unit, "UTF-8")).append(" 账单</h3>")
                    .append("<table width='100%' cellspacing='0' cellpadding='5' style='border-collapse:collapse;font-size:12px'>")
                    .append("<thead style='background:#2934ea;color:white'><tr>");
            for (String label : List.of("月份 / 产品", "请求次数", "调用次数", "计费次数", "计费单价", "账单费用"))
                out.append("<th scope='col'>").append(label).append("</th>");
            out.append("</tr></thead><tbody>");
            appendRow(out, "本期合计", bill.period.total, "background:#dee3fa;font-weight:bold", false);
            bill.months.forEach((month, section) -> {
                appendRow(out, month + " 月度合计", section.total, "background:#f0f2fc;font-weight:bold", false);
                section.byProduct.forEach((id, line) -> appendRow(out, products.get(id).getBillingName(), line, "", true));
            });
            out.append("</tbody></table>");
        });
        out.append("<p style='font-size:12px;color:#59647a'>请求次数按各产品的日汇总累加。</p>");
        if (currencies.values().stream().anyMatch(section -> section.period.total.requests == null))
            out.append("<p style='font-size:12px;color:#59647a'>部分明细缺少请求标识，对应请求次数标为“未记录”。</p>");
        return out.toString();
    }

    private static void appendRow(StringBuilder out, String label, Totals totals, String style, boolean product) {
        out.append("<tr style='").append(style).append("'><th scope='row' style='text-align:left;font-weight:inherit;")
                .append(product ? "padding-left:24px;" : "").append("border-bottom:1px solid #d9dfee'>")
                .append(HtmlUtils.htmlEscape(label, "UTF-8")).append("</th>");
        for (Object value : List.of(totals.requestsValue(), totals.calls, totals.charged,
                product ? totals.priceValue() : "—", amountText(totals.amount))) {
            out.append("<td style='text-align:right;border-bottom:1px solid #d9dfee'>")
                    .append(HtmlUtils.htmlEscape(number(value), "UTF-8")).append("</td>");
        }
        out.append("</tr>");
    }
}
