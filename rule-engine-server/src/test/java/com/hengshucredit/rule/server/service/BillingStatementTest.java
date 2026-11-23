package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import com.hengshucredit.rule.server.consolelogin.ConsoleMailService;
import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.mapper.RuleBillingConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleBillingSummaryMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;


public class BillingStatementTest {
    @Test public void sendsOneRecipientReconciledMonthlyAmountAndTemplateWorkbook() throws Exception {
        var first = day(3, "0.037125", "0.074250", 2);
        var second = day(1, "0.1", "0.1", 1);
        var config = new RuleBillingConfig();
        config.setBillingName("活体<核验>");
        config.setChargeType("SUCCESS");
        var bill = new BillingStatement(config, "客户A", first, List.of(first, second));
        assertTrue(bill.html().contains("活体&lt;核验&gt;"));
        assertTrue(bill.html().contains("CNY 0.17425"));

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
        service.sendStatement("accounts@example.test", bill.title(), bill.plain(), bill.html(), "月度账单.xlsx", bill.workbook());
        assertEquals(1, captured.size());
        var bytes = new ByteArrayOutputStream();
        captured.get(0).writeTo(bytes);
        var delivered = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(1, delivered.getAllRecipients().length);
        assertEquals("accounts@example.test", delivered.getAllRecipients()[0].toString());
        assertTrue(delivered.getSubject().contains("2026-11"));
        var parts = (MimeMultipart) delivered.getContent();
        var attachment = parts.getBodyPart(1);
        assertTrue(attachment.isMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        try (var book = new XSSFWorkbook(attachment.getInputStream())) {
            assertEquals(3, book.getNumberOfSheets());
            assertEquals("客户A", book.getSheet("计费产品清单").getRow(4).getCell(2).getStringCellValue());
            var monthly = book.getSheet("月度账单").getRow(2);
            assertEquals(CellType.BLANK, monthly.getCell(4).getCellType());
            assertEquals(4, monthly.getCell(5).getNumericCellValue(), 0);
            assertEquals(3, monthly.getCell(6).getNumericCellValue(), 0);
            assertEquals(0.17425, monthly.getCell(8).getNumericCellValue(), 1e-12);
            assertEquals("多档", book.getSheetAt(1).getRow(3).getCell(7).getStringCellValue());
            assertEquals(0.037125, book.getSheet("日账单明细").getRow(2).getCell(7).getNumericCellValue(), 1e-12);
            assertEquals(0.1, book.getSheetAt(2).getRow(3).getCell(8).getNumericCellValue(), 1e-12);
        }
        assertFalse(ConsoleMailService.validBillRecipient("a@example.test,b@example.test"));
        assertFalse(ConsoleMailService.validBillRecipient("a@example.test\r\nBcc: b@example.test"));
    }

    @Test public void monthUsesOnlySelectedAccountProjectTargetCurrencyAndAuthorization() throws Exception {
        org.junit.Assume.assumeTrue(Boolean.getBoolean("billingSummary.integration"));
        try (var db = new BillingSummaryTestDatabase()) {
            String schema = new org.springframework.core.io.ClassPathResource("sql/schema.sql")
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `rule_project`");
            db.jdbc.execute(schema.substring(start, schema.indexOf(';', start) + 1));
            db.createTable("rule_billing_config");
            db.jdbc.update("INSERT INTO rule_project(id,project_code,project_name) VALUES(1,'P','账单测试项目')");
            db.jdbc.update("INSERT INTO rule_billing_config(id,project_id,billing_code,billing_name,bill_email) VALUES(7,1,'V','服务','billing@example.test')");
            db.jdbc.update("""
                    INSERT INTO rule_billing_record(billing_config_id,actor_user_id,project_id,auth_id,target_ref_id,
                         currency,occur_time,billing_target,billing_code,request_id,amount,unit_price)
                    VALUES (7,3,1,1,9,'USD','2026-11-01','ENGINE','V','R1',1.20,1.20),
                           (7,3,1,1,9,'USD','2026-11-02','ENGINE','V','R2',1.20,1.20),
                           (7,4,1,1,9,'USD','2026-11-01','ENGINE','V','R1',999,999),
                           (7,3,2,1,9,'USD','2026-11-01','ENGINE','V','R1',999,999),
                           (7,3,1,2,9,'USD','2026-11-01','ENGINE','V','R1',999,999),
                           (7,3,1,1,8,'USD','2026-11-01','ENGINE','V','R1',999,999),
                           (7,3,1,1,9,'CNY','2026-11-01','ENGINE','V','R1',999,999),
                           (8,3,1,1,9,'USD','2026-11-01','ENGINE','OTHER','R1',999,999),
                           (7,3,1,1,9,'USD','2026-10-31','ENGINE','V','R1',999,999)
                    """);
            var first = LocalDate.of(2026,11,1);
            for (var date : List.of(first.minusDays(1), first, first.plusDays(1))) db.service.refreshSummary(date);
            long id = db.jdbc.queryForObject("SELECT id FROM rule_billing_summary WHERE summary_date='2026-11-02'", Long.class);
            db.sessions.getConfiguration().addMapper(RuleProjectMapper.class);
            db.sessions.getConfiguration().addMapper(RuleBillingConfigMapper.class);
            var recipients = new java.util.ArrayList<String>();
            var mail = new ConsoleMailService() {
                @Override public void sendStatement(String email, String title, String plain, String body, String filename, byte[] attachment) {
                    recipients.add(email);
                    assertTrue(plain.contains("USD 2.4"));
                    assertFalse(body.contains("999"));
                    assertTrue(attachment.length > 0);
                }
            };
            var service = new BillingStatementService(db.sessions.getMapper(RuleBillingSummaryMapper.class),
                    db.sessions.getMapper(RuleBillingConfigMapper.class), db.sessions.getMapper(RuleProjectMapper.class),
                    new ConsoleProjectAccess(), mail);
            service.send(id);
            db.jdbc.update("UPDATE rule_billing_config SET bill_email=NULL WHERE id=7");
            assertThrows(IllegalArgumentException.class, () -> service.send(id));
            assertEquals(List.of("billing@example.test"), recipients);
        }
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
        row.setProjectCode("P");
        return row;
    }
}
