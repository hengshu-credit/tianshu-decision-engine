package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class BillingStatement {
    private final RuleBillingConfig config;
    private final String project;
    private final RuleBillingSummary selected;
    private final List<RuleBillingSummary> days;
    private final YearMonth month;
    private final Long requests;
    private final long calls;
    private final long charged;
    private final BigDecimal amount;
    private final BigDecimal price;

    BillingStatement(RuleBillingConfig config, String project, RuleBillingSummary selected, List<RuleBillingSummary> days) {
        this.config = config;
        this.project = project;
        this.selected = selected;
        this.days = days;
        month = YearMonth.from(selected.getSummaryDate());
        requests = days.stream().allMatch(day -> day.getRequestCount() != null)
                ? days.stream().mapToLong(RuleBillingSummary::getRequestCount).sum() : null;
        calls = days.stream().mapToLong(RuleBillingSummary::getTotalCount).sum();
        charged = days.stream().mapToLong(RuleBillingSummary::getChargedCount).sum();
        amount = days.stream().map(RuleBillingSummary::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal first = days.get(0).getBilledUnitPrice();
        price = days.stream().allMatch(day -> Objects.equals(first, day.getBilledUnitPrice())) ? first : null;
    }

    String title() { return month + " 数据服务账单 · " + config.getBillingName().replaceAll("[\\r\\n]+", " "); }
    private String scope() {
        String auth = Objects.requireNonNullElse(selected.getAuthCode(),
                selected.getAuthId() == null ? "未记录" : selected.getAuthId().toString());
        return "计费项 " + selected.getBillingCode() + "，鉴权 " + auth + "，币种 " + selected.getCurrency();
    }
    private String money() { return selected.getCurrency() + " " + amount.stripTrailingZeros().toPlainString(); }
    String plain() { return project + "团队，您好！请查收" + title() + "。本期已汇总金额为 " + money() + "，日明细见附件。"; }

    String html() {
        var values = Map.of("project", project, "period", month.toString(), "product", config.getBillingName(),
                "scope", scope(), "money", money(), "requests", requests == null ? "—" : requests.toString(), "calls", Long.toString(calls),
                "charged", Long.toString(charged), "price", price == null ? "多档" : price.stripTrailingZeros().toPlainString());
        try {
            String template = new ClassPathResource("billing/statement.html").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.regex.Pattern.compile("\\{\\{(\\w+)}}").matcher(template)
                    .replaceAll(match -> java.util.regex.Matcher.quoteReplacement(HtmlUtils.htmlEscape(values.get(match.group(1)))));
        } catch (IOException error) {
            throw new IllegalStateException("账单邮件模板读取失败", error);
        }
    }

    byte[] workbook() {
        try (var source = new ClassPathResource("billing/statement.xlsx").getInputStream();
             var book = new XSSFWorkbook(source); var out = new ByteArrayOutputStream()) {
            var numberFormat = book.createDataFormat().getFormat("#,##0.00####");
            for (String sheetName : List.of("月度账单", "日账单明细")) {
                Sheet sheet = book.getSheet(sheetName);
                for (int r = 2; r <= 3; r++) {
                    for (int c = 7; c <= 8; c++) {
                        var cell = sheet.getRow(r).getCell(c);
                        var style = book.createCellStyle();
                        style.cloneStyleFrom(cell.getCellStyle());
                        style.setDataFormat(numberFormat);
                        cell.setCellStyle(style);
                    }
                }
            }
            var catalog = book.getSheet("计费产品清单");
            catalog.getRow(3).getCell(6).setCellValue("计费方式");
            String target = Map.of("ENGINE", "规则引擎", "API", "外数 API", "DB", "数据库查询").get(selected.getBillingTarget());
            String type = Map.of("COUNT", "按次", "SUCCESS", "成功计费", "DURATION", "按耗时", "FIXED", "固定金额").get(config.getChargeType());
            write(catalog, 4, 4, 1, project, selected.getProjectCode(), config.getBillingName(), target, type,
                    (price == null ? "多档" : price.stripTrailingZeros().toPlainString()) + " " + selected.getCurrency());
            var monthly = book.getSheet("月度账单");
            String note = scope() + "。请求次数按日去重，缺少请求标识时留空。";
            write(monthly, 2, 2, month.toString(), project, "当月已汇总合计", requests, calls, charged, null, amount, note);
            write(monthly, 3, 3, null, null, config.getBillingName(), requests, calls, charged,
                    price == null ? "多档" : price, amount, null);
            var daily = book.getSheet("日账单明细");
            for (int i = 0; i < days.size(); i++) {
                var day = days.get(i);
                write(daily, i + 2, 2 + i % 2, day.getSummaryDate().toString(), project, config.getBillingName(),
                        day.getRequestCount(), day.getTotalCount(), day.getChargedCount(),
                        day.getBilledUnitPrice() == null ? "多档" : day.getBilledUnitPrice(), day.getTotalAmount());
            }
            book.write(out);
            return out.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("账单附件生成失败", error);
        }
    }

    private static void write(Sheet sheet, int index, int styleRowIndex, Object... values) {
        Row styleRow = sheet.getRow(styleRowIndex);
        Row row = sheet.getRow(index);
        if (row == null) {
            row = sheet.createRow(index);
            row.setHeight(styleRow.getHeight());
        }
        for (int i = 0; i < values.length; i++) {
            int column = i + 1;
            var cell = row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            CellStyle style = styleRow.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).getCellStyle();
            cell.setCellStyle(style);
            if (values[i] instanceof Number number) cell.setCellValue(number.doubleValue());
            else if (values[i] == null) cell.setBlank();
            else cell.setCellValue(values[i].toString());
        }
    }
}
