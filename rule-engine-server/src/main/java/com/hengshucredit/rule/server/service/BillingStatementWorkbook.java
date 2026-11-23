package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/** 三个模板工作表只为本账单中的数据创建行。 */
final class BillingStatementWorkbook {
    private final XSSFWorkbook book;
    private final BillingStatement bill;
    private final XSSFCellStyle header, light, white, subtotal;
    private final Map<Short, CellStyle> decimals = new java.util.HashMap<>();

    private BillingStatementWorkbook(XSSFWorkbook book, BillingStatement bill) {
        this.book = book;
        this.bill = bill;
        header = style(0x2934EA, true, true);
        light = style(0xEFF1FF, false, false);
        white = style(0xFFFFFF, false, false);
        subtotal = style(0xDEE3FA, true, false);
    }

    static byte[] render(BillingStatement bill, byte[] logo) {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            // POI 新工作簿已有 xf 0，显式注册其 Excel 内置名称，供其他表格阅读器识别。
            String namespace = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
            try (var cursor = book.getStylesSource().getCTStylesheet().newCursor()) {
                cursor.toEndToken();
                cursor.beginElement("cellStyles", namespace);
                cursor.insertAttributeWithValue("count", "1");
                cursor.beginElement("cellStyle", namespace);
                cursor.insertAttributeWithValue("name", "Normal");
                cursor.insertAttributeWithValue("xfId", "0");
                cursor.insertAttributeWithValue("builtinId", "0");
            }
            var writer = new BillingStatementWorkbook(book, bill);
            writer.catalog(logo);
            writer.monthly();
            writer.daily();
            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("账单附件生成失败", e);
        }
    }

    private void catalog(byte[] logo) throws IOException {
        Sheet sheet = sheet("计费产品清单", 3, "序号", "项目名称", "项目编码", "计费名称", "计费对象", "计费方式", "单价");
        Row title = sheet.createRow(1);
        title.setHeightInPoints(36);
        var heading = title.createCell(2);
        heading.setCellValue(bill.company);
        var titleStyle = style(0xFFFFFF, true, false);
        titleStyle.setAlignment(HorizontalAlignment.LEFT);
        heading.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 2, 7));
        var image = ImageIO.read(new ByteArrayInputStream(logo));
        if (image == null) throw new IOException("系统 Logo 无法解码");
        var anchor = book.getCreationHelper().createClientAnchor();
        anchor.setCol1(1);
        anchor.setCol2(1);
        anchor.setRow1(1);
        anchor.setRow2(1);
        double scale = 40.0 / Math.max(image.getWidth(), image.getHeight());
        anchor.setDx1(Units.pixelToEMU(4));
        anchor.setDx2(Units.pixelToEMU(4 + Math.max(1, (int)Math.round(image.getWidth() * scale))));
        anchor.setDy1(Units.pixelToEMU(4));
        anchor.setDy2(Units.pixelToEMU(4 + Math.max(1, (int)Math.round(image.getHeight() * scale))));
        sheet.createDrawingPatriarch().createPicture(anchor, book.addPicture(logo, Workbook.PICTURE_TYPE_PNG));
        int index = 0;
        for (var item : bill.period.byProduct.entrySet()) {
            RuleBillingConfig config = bill.products.get(item.getKey());
            String target = switch (config.getBillingTarget()) {
                case "ENGINE" -> "规则引擎"; case "API" -> "外数 API"; case "DB" -> "数据库查询";
                default -> config.getBillingTarget();
            };
            String type = switch (config.getChargeType()) {
                case "SUCCESS" -> "成功计费"; case "COUNT" -> "按次"; case "FIXED" -> "固定金额"; case "DURATION" -> "按耗时";
                default -> config.getChargeType();
            };
            append(sheet, band(index), ++index, bill.project, bill.projectCode, config.getBillingName(), target, type,
                    BillingStatement.number(item.getValue().priceValue()) + " " + bill.currency);
        }
    }

    private void monthly() {
        Sheet sheet = sheet("月度账单", 1, "统计日期", "项目名称", "计费名称", "请求次数", "调用次数", "计费次数", "计费单价", "账单费用", "备注");
        bill.months.forEach((month, section) -> {
            int index = 0;
            var total = section.total;
            append(sheet, subtotal, month, bill.project, "当月合计", total.requestsValue(), total.calls, total.charged,
                    "按产品列示", total.amount, bill.currency + " · " + section.byProduct.size() + " 项产品");
            for (var product : section.byProduct.entrySet()) {
                var config = bill.products.get(product.getKey());
                var line = product.getValue();
                append(sheet, band(index++), month, bill.project, config.getBillingName(), line.requestsValue(),
                        line.calls, line.charged, line.priceValue(), line.amount, bill.currency + " · " + config.getBillingCode());
            }
        });
    }

    private void daily() {
        Sheet sheet = sheet("日账单明细", 1, "统计日期", "项目名称", "计费名称", "请求次数", "调用次数", "计费次数", "计费价格", "账单费用", "币种");
        int index = 0;
        for (var day : bill.days.entrySet()) {
            for (var item : day.getValue().byProduct.entrySet()) {
                var line = item.getValue();
                append(sheet, band(index++), day.getKey(), bill.project, bill.products.get(item.getKey()).getBillingName(),
                        line.requestsValue(), line.calls, line.charged, line.priceValue(), line.amount, bill.currency);
            }
        }
    }

    private CellStyle band(int index) { return index % 2 == 0 ? light : white; }

    private Sheet sheet(String name, int headerIndex, String... labels) {
        Sheet sheet = book.createSheet(name);
        sheet.setDisplayGridlines(false);
        sheet.setColumnWidth(0, 3 * 256);
        sheet.createFreezePane(0, headerIndex + 1);
        Row row = sheet.createRow(headerIndex);
        row.setHeightInPoints(24);
        for (int i = 0; i < labels.length; i++) {
            var cell = row.createCell(i + 1);
            cell.setCellStyle(header);
            cell.setCellValue(labels[i]);
            sheet.setColumnWidth(i + 1, 15 * 256);
        }
        return sheet;
    }

    private void append(Sheet sheet, CellStyle style, Object... values) {
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        int lines = 1;
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value == null) throw new IllegalArgumentException("账单模板字段缺少数据：" + sheet.getSheetName());
            var cell = row.createCell(i + 1);
            cell.setCellStyle(style);
            if (value instanceof BigDecimal decimal) {
                cell.setCellStyle(decimals.computeIfAbsent(style.getIndex(), key -> {
                    var money = book.createCellStyle();
                    money.cloneStyleFrom(style);
                    money.setDataFormat(book.createDataFormat().getFormat("#,##0.00####"));
                    return money;
                }));
                cell.setCellValue(decimal.doubleValue());
            } else if (value instanceof Number number) cell.setCellValue(number.doubleValue());
            else cell.setCellValue(value.toString());
            int width = BillingStatement.number(value).codePoints().map(c -> c > 127 ? 2 : 1).sum() + 4;
            int column = i + 1;
            sheet.setColumnWidth(column, Math.max(sheet.getColumnWidth(column), Math.min(64, width) * 256));
            lines = Math.max(lines, (width + 63) / 64);
        }
        row.setHeightInPoints(20f * lines);
    }

    private XSSFCellStyle style(int rgb, boolean bold, boolean inverted) {
        var style = book.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new Color(rgb), null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setWrapText(true);
        var font = book.createFont();
        font.setFontName("Microsoft YaHei");
        font.setFontHeightInPoints((short) 10);
        font.setBold(bold);
        font.setColor(new XSSFColor(new Color(inverted ? 0xFFFFFF : 0x24344E), null));
        style.setFont(font);
        return style;
    }
}
