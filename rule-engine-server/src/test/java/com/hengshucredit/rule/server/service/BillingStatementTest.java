package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import com.hengshucredit.rule.server.consolelogin.ConsoleMailService;
import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.mapper.RuleBillingConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleBillingSummaryMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserProjectMapper;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import com.hengshucredit.rule.server.system.EngineMailFont;
import com.hengshucredit.rule.server.system.MailTheme;
import com.hengshucredit.rule.model.dto.ConsoleThemePreference;
import com.hengshucredit.rule.server.system.SystemBrandAssetService;
import com.hengshucredit.rule.server.system.SystemBrandAssetService.StatementBrand;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;

public class BillingStatementTest {
    @Test public void sendsOneRecipientReconciledMonthlyAmountAndTemplateWorkbook() throws Exception {
        var first = day(3, "0.037125", "0.074250", 2);
        var second = day(1, "0.1", "0.1", 1);
        var config = product(7, "活体<核验>");
        var another = product(8, "信用评分");
        var third = day(2, "0.012", "0.024", 2);
        third.setBillingConfigId(8L);
        third.setSummaryDate(LocalDate.of(2026, 10, 31));
        third.setRequestCount(2L);
        var bill = new BillingStatement("客户A", "PROJECT_A", "对账测试公司", third.getSummaryDate(), first.getSummaryDate(),
                Map.of(7L, config, 8L, another), List.of(first, second, third));
        var brand = brand();
        assertTrue(bill.tablesHtml(MailTheme.DEFAULT).contains("活体&lt;核验&gt;"));
        assertTrue(bill.tablesHtml(MailTheme.DEFAULT).contains("CNY 账单"));
        assertTrue(bill.tablesHtml(MailTheme.DEFAULT).contains(">0.19825</td>"));
        assertTrue(bill.tablesHtml(MailTheme.DEFAULT).contains("2026-10</th>"));
        assertTrue(bill.tablesHtml(MailTheme.DEFAULT).contains("2026-11</th>"));
        assertFalse(bill.tablesHtml(MailTheme.DEFAULT).contains("减免"));

        var captured = new java.util.ArrayList<MimeMessage>();
        var transport = new JavaMailSenderImpl() {
            @Override protected void doSend(MimeMessage[] messages, Object[] originals) {
                captured.addAll(List.of(messages));
            }
        };
        var props = new RuleEngineConsoleLoginProperties();
        props.getMail().setFrom("sender@example.test");
        var service = new ConsoleMailService();
        ReflectionTestUtils.setField(service, "mailSender", transport);
        ReflectionTestUtils.setField(service, "properties", props);
        service.sendStatement(brand, "accounts@example.test", bill.title(), "客户A团队", bill.introduction(), bill.plain(),
                bill.tablesHtml(brand.theme()), "账单.xlsx", BillingStatementWorkbook.render(bill, brand));
        assertEquals(1, captured.size());
        var bytes = new ByteArrayOutputStream();
        captured.get(0).writeTo(bytes);
        var delivered = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(1, delivered.getAllRecipients().length);
        assertEquals("accounts@example.test", delivered.getAllRecipients()[0].toString());
        assertTrue(delivered.getSubject().contains("2026-11"));
        var parts = (MimeMultipart) delivered.getContent();
        var related = (MimeMultipart) parts.getBodyPart(0).getContent();
        assertArrayEquals(brand.logo(), related.getBodyPart(1).getInputStream().readAllBytes());
        var alternatives = (MimeMultipart) related.getBodyPart(0).getContent();
        String html = (String) alternatives.getBodyPart(1).getContent();
        for (String text : List.of("cid:tianshu-logo", brand.name(), "上海衡枢真信科技有限公司", "https://www.hengshucredit.com", "尊敬的客户A团队", "本期合计", "0.17425", "顺颂商祺！", "mailto:hscredit@hengshucredit.com"))
            assertTrue(text, html.contains(text));
        assertFalse(html.contains(bill.company));
        assertTrue(html.contains("background:" + brand.theme().surface()));
        assertTrue(html.contains("scrollbar-color: " + brand.theme().accent()));
        assertTrue(html.contains("overflow-x:auto"));
        assertTrue(html.contains("white-space:nowrap"));
        assertTrue(html.contains("cid:tianshu-font"));
        assertTrue(html.contains("font-size:12px;line-height:1.7"));
        assertArrayEquals(brand.font().file().getContentAsByteArray(), related.getBodyPart(2).getInputStream().readAllBytes());
        var attachment = parts.getBodyPart(1);
        assertTrue(attachment.isMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        try (var book = new XSSFWorkbook(attachment.getInputStream())) {
            assertEquals(3, book.getNumberOfSheets());
            assertEquals("客户A", book.getSheet("计费产品清单").getRow(4).getCell(2).getStringCellValue());
            var catalog = book.getSheetAt(0);
            assertEquals(bill.company, catalog.getRow(1).getCell(2).getStringCellValue());
            assertArrayEquals(brand.logo(), book.getAllPictures().get(0).getData());
            assertEquals(5, catalog.getLastRowNum());
            assertEquals(6, book.getSheetAt(1).getLastRowNum());
            assertEquals("本期合计", book.getSheetAt(1).getRow(2).getCell(4).getStringCellValue());
            assertEquals(0.19825, book.getSheetAt(1).getRow(2).getCell(9).getNumericCellValue(), 1e-12);
            assertEquals("FF1C7965", catalog.getRow(3).getCell(1).getCellStyle().getFillForegroundXSSFColor().getARGBHex());
            assertFalse(catalog.getRow(4).getCell(1).getCellStyle().getWrapText());
            assertEquals(brand.font().family(), catalog.getRow(3).getCell(1).getCellStyle().getFont().getFontName());
            assertEquals(4, book.getSheetAt(2).getLastRowNum());
            for (var sheet : book) {
                int header = sheet == catalog ? 3 : 1;
                for (int r = header; r <= sheet.getLastRowNum(); r++) {
                    for (int c = 1; c < sheet.getRow(header).getLastCellNum(); c++)
                        assertFalse(sheet.getRow(r).getCell(c).toString().isBlank());
                }
            }
            var monthly = book.getSheet("月度账单").getRow(3);
            assertEquals("未记录", monthly.getCell(5).getStringCellValue());
            assertEquals(4, monthly.getCell(6).getNumericCellValue(), 0);
            assertEquals(3, monthly.getCell(7).getNumericCellValue(), 0);
            assertEquals(0.17425, monthly.getCell(9).getNumericCellValue(), 1e-12);
            assertEquals("0.037125–0.1", book.getSheetAt(1).getRow(4).getCell(8).getStringCellValue());
            assertEquals(0.037125, book.getSheet("日账单明细").getRow(2).getCell(7).getNumericCellValue(), 1e-12);
            assertEquals(0.1, book.getSheetAt(2).getRow(3).getCell(8).getNumericCellValue(), 1e-12);
        }
        assertFalse(ConsoleMailService.validBillRecipient("a@example.test,b@example.test"));
        assertFalse(ConsoleMailService.validBillRecipient("a@example.test\r\nBcc: b@example.test"));
    }

    @Test public void sameProductKeepsSeparateMonthlyAndPeriodAmountsInEachCurrency() throws Exception {
        var octCny = day(1, "2", "10", 5);
        var octUsd = day(1, "0.1", "0.50", 5);
        var novCny = day(1, "3", "9", 3);
        var novUsd = day(1, "0.2", "0.60", 3);
        var october = LocalDate.of(2026, 10, 1);
        octCny.setSummaryDate(october);
        octUsd.setSummaryDate(october);
        octUsd.setCurrency("USD");
        novUsd.setCurrency("USD");
        var bill = new BillingStatement("项目", "PRJ", "抬头", october, novCny.getSummaryDate(),
                Map.of(7L, product(7, "计费A")), List.of(octCny, octUsd, novCny, novUsd));
        var doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(("<root>" + bill.tablesHtml(MailTheme.DEFAULT) + "</root>").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(2, doc.getElementsByTagName("table").getLength());
        for (int c = 0; c < 2; c++) {
            String unit = c == 0 ? "CNY" : "USD";
            assertEquals(unit + " 账单", doc.getElementsByTagName("h3").item(c).getTextContent());
            var rows = ((org.w3c.dom.Element)doc.getElementsByTagName("tbody").item(c)).getElementsByTagName("tr");
            var names = List.of("本期合计", "2026-11", "2026-11", "2026-10", "2026-10");
            var amounts = c == 0 ? List.of("19.00","9.00","9.00","10.00","10.00") : List.of("1.10","0.60","0.60","0.50","0.50");
            assertEquals(names.size(), rows.getLength());
            for (int i = 0; i < rows.getLength(); i++) {
                assertEquals(names.get(i), rows.item(i).getFirstChild().getTextContent());
                assertEquals(7, rows.item(i).getChildNodes().getLength());
                assertEquals(List.of("—", "月度合计", "计费A", "月度合计", "计费A").get(i),
                        rows.item(i).getChildNodes().item(1).getTextContent());
                assertEquals(amounts.get(i), rows.item(i).getLastChild().getTextContent());
            }
        }
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(BillingStatementWorkbook.render(bill, brand())))) {
            assertEquals("2–3", book.getSheetAt(0).getRow(4).getCell(7).getStringCellValue());
            assertEquals("USD", book.getSheetAt(0).getRow(5).getCell(8).getStringCellValue());
            var amounts = new java.util.TreeMap<String, Double>();
            var monthly = book.getSheetAt(1);
            for (int r = 2; r <= monthly.getLastRowNum(); r++) {
                var row = monthly.getRow(r);
                assertEquals(r < 7 ? "CNY" : "USD", row.getCell(1).getStringCellValue());
                assertEquals(List.of("本期合计","当月合计","计费A","当月合计","计费A").get((r-2)%5), row.getCell(4).getStringCellValue());
                assertEquals(List.of(0,1,2,1,2).get((r-2)%5).intValue(), row.getOutlineLevel());
                String label = row.getCell(4).getStringCellValue();
                if (!label.endsWith("合计")) continue;
                String period = label.equals("本期合计") ? label : row.getCell(2).getStringCellValue();
                assertNull(amounts.put(period + row.getCell(1).getStringCellValue(), row.getCell(9).getNumericCellValue()));
            }
            assertEquals(Map.of("本期合计CNY", 19d, "本期合计USD", 1.1d, "2026-10CNY", 10d, "2026-10USD", 0.5d,
                    "2026-11CNY", 9d, "2026-11USD", 0.6d), amounts);
        }
    }

    @Test public void stylesExtendPastTheSampleAndStopAtLastDay() throws Exception {
        var start = LocalDate.of(2026, 10, 1);
        var rows = new java.util.ArrayList<RuleBillingSummary>();
        for (int i = 0; i < 45; i++) {
            var row = day(1, "0.15", "0.30", 2);
            row.setSummaryDate(start.plusDays(i));
            rows.add(row);
        }
        var bill = new BillingStatement("甲项目", "P", "系统甲", start, start.plusDays(44),
                Map.of(7L, product(7, "服务")), rows);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(BillingStatementWorkbook.render(bill, brand())))) {
            var sheet = book.getSheetAt(2);
            assertEquals(46, sheet.getLastRowNum());
            assertEquals(sheet.getRow(2).getCell(1).getCellStyle(), sheet.getRow(46).getCell(1).getCellStyle());
            assertNotEquals(sheet.getRow(45).getCell(1).getCellStyle(), sheet.getRow(46).getCell(1).getCellStyle());
            assertEquals(4, book.getSheetAt(0).getLastRowNum());
        }
    }

    @Test public void monthUsesOnlyMatchingRecipientCompanyAccountProjectCurrencyAndAuthorization() throws Exception {
        org.junit.Assume.assumeTrue(Boolean.getBoolean("billingSummary.integration"));
        try (var db = new BillingSummaryTestDatabase()) {
            String schema = new org.springframework.core.io.ClassPathResource("sql/schema.sql")
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `rule_project`");
            db.jdbc.execute(schema.substring(start, schema.indexOf(';', start) + 1));
            db.createTable("rule_billing_config");
            db.jdbc.update("INSERT INTO rule_project(id,project_code,project_name) VALUES(1,'P','账单测试项目')");
            db.jdbc.update("INSERT INTO rule_billing_config(id,project_id,billing_code,billing_name,bill_email) VALUES(7,1,'V','服务','billing@example.test')");
            db.jdbc.update("INSERT INTO rule_billing_config(id,project_id,billing_code,billing_name,bill_email,bill_company_name) VALUES "
                    + "(8,1,'OTHER','其他收件人','other@example.test',NULL),(9,1,'ANOTHER','不同抬头','billing@example.test','乙公司'),"
                    + "(10,1,'SCORE','信用评分','billing@example.test',NULL)");
            db.jdbc.update("""
                    INSERT INTO rule_billing_record(billing_config_id,actor_user_id,project_id,auth_id,target_ref_id,
                         currency,occur_time,billing_target,billing_code,request_id,amount,unit_price)
                    VALUES (7,3,1,1,9,'USD','2026-11-01','ENGINE','V','R1',0.80,0.80),
                           (7,3,1,1,9,'USD','2026-11-01','ENGINE','V','R2',0.40,0.40),
                           (7,3,1,1,9,'USD','2026-11-02','ENGINE','V','R2',1.20,1.20),
                           (7,4,1,1,9,'USD','2026-11-01','ENGINE','V','R1',999,999),
                           (7,3,2,1,9,'USD','2026-11-01','ENGINE','V','R1',999,999),
                           (7,3,1,2,9,'USD','2026-11-01','ENGINE','V','R1',999,999),
                           (9,3,1,1,8,'USD','2026-11-01','ENGINE','V','R1',999,999),
                           (7,3,1,1,9,'CNY','2026-11-01','ENGINE','V','R1',5,5),
                           (8,3,1,1,9,'USD','2026-11-01','ENGINE','OTHER','R1',999,999),
                           (10,3,1,1,8,'USD','2026-10-31','API','SCORE','R3',2,2)
                    """);
            var first = LocalDate.of(2026,11,1);
            for (var date : List.of(first.minusDays(1), first, first.plusDays(1))) db.service.refreshSummary(date);
            var prices = db.jdbc.queryForMap("SELECT min_unit_price, max_unit_price, billed_unit_price FROM rule_billing_summary "
                    + "WHERE summary_date='2026-11-01' AND actor_user_id=3 AND project_id=1 AND auth_id=1 AND billing_config_id=7 AND currency='USD'");
            assertNull(prices.get("billed_unit_price"));
            assertEquals(new BigDecimal("0.400000"), prices.get("min_unit_price"));
            assertEquals(new BigDecimal("0.800000"), prices.get("max_unit_price"));
            long id = db.jdbc.queryForObject("SELECT id FROM rule_billing_summary WHERE summary_date='2026-11-02'", Long.class);
            db.sessions.getConfiguration().addMapper(ConsoleUserProjectMapper.class);
            db.sessions.getConfiguration().addMapper(RuleBillingConfigMapper.class);
            var recipients = new java.util.ArrayList<String>();
            var mail = new ConsoleMailService() {
                @Override public void sendStatement(StatementBrand brand, String email, String title, String recipient,
                        String intro, String plain, String body, String filename, byte[] attachment) {
                    recipients.add(email);
                    try (var book = new XSSFWorkbook(new ByteArrayInputStream(attachment))) {
                        assertEquals(recipients.size() == 3 ? "结算专用抬头" : brand.name(),
                                book.getSheetAt(0).getRow(1).getCell(2).getStringCellValue());
                    } catch (java.io.IOException failure) { throw new AssertionError(failure); }
                    assertTrue(plain.contains(recipients.size() == 2 ? "USD 4.40" : "USD 2.40"));
                    assertTrue(plain.contains("CNY 5.00"));
                    assertTrue(body.contains("0.4–1.2"));
                    assertFalse(body.contains("999"));
                    assertEquals(recipients.size() == 2, body.contains("信用评分"));
                    assertTrue(attachment.length > 0);
                }
            };
            var brand = brand();
            var assets = new SystemBrandAssetService(null, null) {
                @Override public StatementBrand statementBrand() { return brand; }
            };
            var service = new BillingStatementService(db.sessions.getMapper(RuleBillingSummaryMapper.class),
                    db.sessions.getMapper(RuleBillingConfigMapper.class), db.sessions.getMapper(ConsoleUserProjectMapper.class),
                    new ConsoleProjectAccess(), mail, assets);
            service.send(id, null, null);
            service.send(id, first.minusDays(1), first.plusDays(1));
            db.jdbc.update("UPDATE rule_billing_config SET bill_company_name='结算专用抬头' WHERE id=7");
            service.send(id, first.minusDays(1), first.plusDays(1));
            db.jdbc.update("UPDATE rule_billing_config SET bill_email=NULL WHERE id=7");
            assertThrows(IllegalArgumentException.class, () -> service.send(id, null, null));
            assertEquals(List.of("billing@example.test", "billing@example.test", "billing@example.test"), recipients);
        }
    }

    private static StatementBrand brand() throws Exception {
        var logo = new org.springframework.core.io.ClassPathResource("brand/tianshu-logo.png").getContentAsByteArray();
        var pref = new ConsoleThemePreference();
        pref.setColorScheme("DARK");
        pref.setAccentPreset("PEACH_PINK_GRADIENT");
        pref.setAccentMode("CUSTOM_GRADIENT");
        pref.setCustomGradientColors(List.of("#1C7965", "#3975C9"));
        var theme = MailTheme.from(pref);
        assertEquals("#151D31", theme.surface());
        assertEquals("#3975C9", theme.scrollbarEnd());
        var fallback = EngineMailFont.engineDefault();
        var font = new EngineMailFont("账单测试字体", "normal", fallback.file(), fallback.mimeType());
        return new StatementBrand("系统甲", "通知", logo, font, theme);
    }

    private static RuleBillingConfig product(long id, String name) {
        var config = new RuleBillingConfig();
        config.setId(id);
        config.setBillingCode("ITEM_" + id);
        config.setBillingName(name);
        config.setBillingTarget("ENGINE");
        config.setChargeType("SUCCESS");
        return config;
    }

    private RuleBillingSummary day(int date, String price, String amount, long charged) {
        var row = new RuleBillingSummary();
        row.setSummaryDate(LocalDate.of(2026, 11, date));
        row.setCurrency("CNY");
        row.setTotalCount(2L);
        row.setChargedCount(charged);
        row.setBilledUnitPrice(new BigDecimal(price));
        row.setTotalAmount(new BigDecimal(amount));
        row.setBillingTarget("ENGINE");
        row.setBillingConfigId(7L);
        row.setProjectCode("P");
        return row;
    }
}
