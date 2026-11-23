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
        var bill = new BillingStatement("客户A", "PROJECT_A", "对账测试公司", "CNY", third.getSummaryDate(), first.getSummaryDate(),
                Map.of(7L, config, 8L, another), List.of(first, second, third));
        var brand = brand();
        assertTrue(bill.tablesHtml().contains("活体&lt;核验&gt;"));
        assertTrue(bill.tablesHtml().contains("CNY 0.19825"));
        assertTrue(bill.tablesHtml().contains("2026-10 月度账单"));
        assertTrue(bill.tablesHtml().contains("2026-11 月度账单"));
        assertFalse(bill.tablesHtml().contains("减免"));

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
                bill.tablesHtml(), bill.company, "账单.xlsx", BillingStatementWorkbook.render(bill, brand.logo()));
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
        for (String text : List.of("cid:tianshu-logo", brand.name(), bill.company, "尊敬的客户A团队", "本期账单合计", "0.17425"))
            assertTrue(text, html.contains(text));
        assertFalse(html.contains("上海衡枢真信科技有限公司"));
        var attachment = parts.getBodyPart(1);
        assertTrue(attachment.isMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        try (var book = new XSSFWorkbook(attachment.getInputStream())) {
            assertEquals(3, book.getNumberOfSheets());
            assertEquals("客户A", book.getSheet("计费产品清单").getRow(4).getCell(2).getStringCellValue());
            var catalog = book.getSheetAt(0);
            assertEquals(bill.company, catalog.getRow(1).getCell(2).getStringCellValue());
            assertArrayEquals(brand.logo(), book.getAllPictures().get(0).getData());
            assertEquals(5, catalog.getLastRowNum());
            assertEquals(5, book.getSheetAt(1).getLastRowNum());
            assertEquals(4, book.getSheetAt(2).getLastRowNum());
            for (var sheet : book) {
                int header = sheet == catalog ? 3 : 1;
                for (int r = header; r <= sheet.getLastRowNum(); r++) {
                    for (int c = 1; c < sheet.getRow(header).getLastCellNum(); c++)
                        assertFalse(sheet.getRow(r).getCell(c).toString().isBlank());
                }
            }
            var monthly = book.getSheet("月度账单").getRow(2);
            assertEquals("未记录", monthly.getCell(4).getStringCellValue());
            assertEquals(4, monthly.getCell(5).getNumericCellValue(), 0);
            assertEquals(3, monthly.getCell(6).getNumericCellValue(), 0);
            assertEquals(0.17425, monthly.getCell(8).getNumericCellValue(), 1e-12);
            assertEquals("多档单价", book.getSheetAt(1).getRow(3).getCell(7).getStringCellValue());
            assertEquals(0.037125, book.getSheet("日账单明细").getRow(2).getCell(7).getNumericCellValue(), 1e-12);
            assertEquals(0.1, book.getSheetAt(2).getRow(3).getCell(8).getNumericCellValue(), 1e-12);
        }
        assertFalse(ConsoleMailService.validBillRecipient("a@example.test,b@example.test"));
        assertFalse(ConsoleMailService.validBillRecipient("a@example.test\r\nBcc: b@example.test"));
    }

    @Test public void stylesExtendPastTheSampleAndStopAtLastDay() throws Exception {
        var start = LocalDate.of(2026, 10, 1);
        var rows = new java.util.ArrayList<RuleBillingSummary>();
        for (int i = 0; i < 45; i++) {
            var row = day(1, "0.15", "0.30", 2);
            row.setSummaryDate(start.plusDays(i));
            rows.add(row);
        }
        var bill = new BillingStatement("甲项目", "P", "系统甲", "USD", start, start.plusDays(44),
                Map.of(7L, product(7, "服务")), rows);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(BillingStatementWorkbook.render(bill, brand().logo())))) {
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
                    VALUES (7,3,1,1,9,'USD','2026-11-01','ENGINE','V','R1',1.20,1.20),
                           (7,3,1,1,9,'USD','2026-11-02','ENGINE','V','R2',1.20,1.20),
                           (7,4,1,1,9,'USD','2026-11-01','ENGINE','V','R1',999,999),
                           (7,3,2,1,9,'USD','2026-11-01','ENGINE','V','R1',999,999),
                           (7,3,1,2,9,'USD','2026-11-01','ENGINE','V','R1',999,999),
                           (9,3,1,1,8,'USD','2026-11-01','ENGINE','V','R1',999,999),
                           (7,3,1,1,9,'CNY','2026-11-01','ENGINE','V','R1',999,999),
                           (8,3,1,1,9,'USD','2026-11-01','ENGINE','OTHER','R1',999,999),
                           (10,3,1,1,8,'USD','2026-10-31','API','SCORE','R3',2,2)
                    """);
            var first = LocalDate.of(2026,11,1);
            for (var date : List.of(first.minusDays(1), first, first.plusDays(1))) db.service.refreshSummary(date);
            long id = db.jdbc.queryForObject("SELECT id FROM rule_billing_summary WHERE summary_date='2026-11-02'", Long.class);
            db.sessions.getConfiguration().addMapper(ConsoleUserProjectMapper.class);
            db.sessions.getConfiguration().addMapper(RuleBillingConfigMapper.class);
            var recipients = new java.util.ArrayList<String>();
            var mail = new ConsoleMailService() {
                @Override public void sendStatement(StatementBrand brand, String email, String title, String recipient,
                        String intro, String plain, String body, String signature, String filename, byte[] attachment) {
                    recipients.add(email);
                    assertEquals(recipients.size() == 3 ? "结算专用抬头" : brand.name(), signature);
                    assertTrue(plain.contains(recipients.size() == 2 ? "USD 4.40" : "USD 2.40"));
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
        return new StatementBrand("系统甲", "通知", logo);
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
