package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.mapper.RuleBillingSummaryMapper;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

/** 本机 MySQL 独有测试库；真实业务表不写入，测试完成后清理。 */
public class BillingSummaryCurrencyIntegrationTest {
    @Test(timeout = 60000)
    public void migratesLegacyIndexAndRebuildsCurrenciesAtomically() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("billingSummary.integration"));
        try (BillingSummaryTestDatabase database = new BillingSummaryTestDatabase()) {
            JdbcTemplate jdbc = database.jdbc;
            jdbc.execute("ALTER TABLE rule_billing_summary DROP INDEX uk_billing_summary_key, "
                    + "ADD UNIQUE KEY uk_billing_summary_key (summary_date, project_code, billing_code, billing_target, target_ref_id, auth_id)");
            LocalDate date = LocalDate.of(2026, 10, 11);
            addRecord(jdbc, "CNY", "1.230000", "2026-10-11 00:00:00", 1, 10);
            addRecord(jdbc, "CNY", "2.340000", "2026-10-11 23:59:59", 0, 21);
            addRecord(jdbc, "USD", "5.670000", "2026-10-11 12:00:00", 1, 30);
            addRecord(jdbc, "USD", "100.000000", "2026-10-10 23:59:59", 1, 1);
            addRecord(jdbc, "USD", "200.000000", "2026-10-12 00:00:00", 1, 1);
            jdbc.update("INSERT INTO rule_billing_summary (summary_date, project_id, project_code, billing_code, "
                    + "billing_target, target_ref_id, auth_id, currency, total_count, total_amount) "
                    + "VALUES (?, 7, 'currency_test', 'ENGINE_CALL', 'ENGINE', 31, 11, 'CNY', 3, 9.24)", date);

            RuleBillingSummaryMapper summaries = database.sessions.getMapper(RuleBillingSummaryMapper.class);
            RuleBillingService target = database.target;
            RuleBillingService service = database.service;

            // 证明只修改聚合而不迁移旧唯一约束会失败，且失败不能丢掉原汇总。
            assertThrows(DuplicateKeyException.class, () -> service.refreshSummary(date));
            assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT COUNT(*) FROM rule_billing_summary", Integer.class));
            assertEquals(new BigDecimal("9.240000"), amount(jdbc, "CNY"));

            MigrationJdbc migrationJdbc = new MigrationJdbc(database.source);
            SchemaSyncService migration = new SchemaSyncService();
            ReflectionTestUtils.setField(migration, "jdbcTemplate", migrationJdbc);
            Method migrate = SchemaSyncService.class.getDeclaredMethod("ensureBillingSummaryAuthUniqueKey");
            migrate.setAccessible(true);
            migrate.invoke(migration);
            assertEquals(1, migrationJdbc.alters);
            assertEquals(List.of("summary_date", "project_code", "billing_code", "billing_target", "target_ref_id", "auth_id", "currency"), columns(jdbc));
            migrate.invoke(migration);
            assertEquals("已迁移索引不能每次启动重建", 1, migrationJdbc.alters);

            for (int rebuild = 0; rebuild < 2; rebuild++) {
                assertEquals(2, service.refreshSummary(date));
                assertEquals(new BigDecimal("3.570000"), amount(jdbc, "CNY"));
                assertEquals(new BigDecimal("5.670000"), amount(jdbc, "USD"));
                assertEquals(Integer.valueOf(2), jdbc.queryForObject("SELECT COUNT(*) FROM rule_billing_summary", Integer.class));
                assertEquals(new BigDecimal("15.50"), jdbc.queryForObject("SELECT avg_cost_time_ms FROM rule_billing_summary WHERE currency='CNY'", BigDecimal.class));
            }

            addRecord(jdbc, "CNY", "1.000000", "2026-10-11 13:00:00", 1, 10);
            RuleBillingSummaryMapper failing = (RuleBillingSummaryMapper) Proxy.newProxyInstance(
                    RuleBillingSummaryMapper.class.getClassLoader(), new Class<?>[]{RuleBillingSummaryMapper.class}, (p, method, args) -> {
                        try {
                            Object result = method.invoke(summaries, args);
                            if ("aggregateDate".equals(method.getName())) throw new IllegalStateException("injected failure after bulk write");
                            return result;
                        }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
            ReflectionTestUtils.setField(target, "summaryMapper", failing);
            assertThrows(IllegalStateException.class, () -> service.refreshSummary(date));
            assertEquals(Integer.valueOf(2), jdbc.queryForObject("SELECT COUNT(*) FROM rule_billing_summary", Integer.class));
            assertEquals(new BigDecimal("3.570000"), amount(jdbc, "CNY"));
            assertEquals(new BigDecimal("5.670000"), amount(jdbc, "USD"));
            assertEquals(Integer.valueOf(6), jdbc.queryForObject("SELECT COUNT(*) FROM rule_billing_record", Integer.class));
            assertEquals(new BigDecimal("310.240000"), jdbc.queryForObject("SELECT SUM(amount) FROM rule_billing_record", BigDecimal.class));
            ReflectionTestUtils.setField(target, "summaryMapper", summaries);
            assertEquals(2, service.refreshSummary(date));
            assertEquals(new BigDecimal("4.570000"), amount(jdbc, "CNY"));
            // 同一鉴权的不同令牌仍合并，不同鉴权保持独立。
            for (int token = 21; token <= 22; token++) {
                jdbc.update("INSERT INTO rule_billing_record (project_id, project_code, billing_code, billing_target, "
                        + "target_ref_id, auth_id, auth_code, token_id, currency, amount, occur_time) "
                        + "VALUES (7, 'currency_test', 'ENGINE_CALL', 'ENGINE', 31, 12, 'PARTNER', ?, 'CNY', 1.11, '2026-10-11 12:00:00')", token);
            }
            assertEquals(3, service.refreshSummary(date));
            assertEquals(Long.valueOf(2), jdbc.queryForObject("SELECT total_count FROM rule_billing_summary WHERE auth_id=12", Long.class));
            assertEquals(new BigDecimal("2.220000"), jdbc.queryForObject("SELECT total_amount FROM rule_billing_summary WHERE auth_id=12", BigDecimal.class));
            assertEquals(Long.valueOf(1), jdbc.queryForObject("SELECT fail_count FROM rule_billing_summary WHERE auth_id=11 AND currency='CNY'", Long.class));
        }
    }

    private static void addRecord(JdbcTemplate jdbc, String currency, String amount, String time, int success, long cost) {
        jdbc.update("INSERT INTO rule_billing_record (project_id, project_code, billing_code, billing_target, target_ref_id, "
                + "auth_id, auth_code, auth_type, currency, amount, occur_time, success, cost_time_ms) "
                + "VALUES (7, 'currency_test', 'ENGINE_CALL', 'ENGINE', 31, 11, 'BASIC_MAIN', 'BASIC', ?, ?, ?, ?, ?)",
                currency, new BigDecimal(amount), time, success, cost);
    }

    private static BigDecimal amount(JdbcTemplate jdbc, String currency) {
        return jdbc.queryForObject("SELECT total_amount FROM rule_billing_summary WHERE currency=?", BigDecimal.class, currency);
    }

    private static List<String> columns(JdbcTemplate jdbc) {
        return jdbc.query("SHOW INDEX FROM rule_billing_summary WHERE Key_name = 'uk_billing_summary_key'",
                (rs, row) -> rs.getString("Column_name"));
    }

    private static final class MigrationJdbc extends JdbcTemplate {
        private int alters;
        private MigrationJdbc(DataSource source) { super(source); }
        @Override public void execute(String sql) {
            if (sql.startsWith("ALTER TABLE")) alters++;
            super.execute(sql);
        }
    }

    static DataSource localDataSource() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("rule-engine-server"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("无法定位仓库根目录");
        Properties settings = LocalIntegrationSettings.load(root);
        InetAddress address = InetAddress.getByName(required(settings, "MYSQL_HOST"));
        if (!address.isLoopbackAddress()) throw new IllegalStateException("账单集成测试仅允许本机MySQL");
        String database = required(settings, "MYSQL_DATABASE");
        if (!database.matches("[A-Za-z0-9_$-]+")) throw new IllegalStateException("MYSQL_DATABASE格式无效");
        int port = Integer.parseInt(required(settings, "MYSQL_PORT"));
        if (port < 1 || port > 65535) throw new IllegalStateException("MYSQL_PORT无效");
        String host = address.getHostAddress();
        if (host.contains(":")) host = '[' + host + ']';
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl("jdbc:mysql://" + host + ':' + port + '/' + database + "?serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=15000");
        source.setUsername(required(settings, "MYSQL_USERNAME"));
        source.setPassword(required(settings, "MYSQL_PASSWORD"));
        return source;
    }

    private static String required(Properties settings, String key) {
        String value = settings.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("本机服务配置缺少 " + key);
        return value;
    }
}
