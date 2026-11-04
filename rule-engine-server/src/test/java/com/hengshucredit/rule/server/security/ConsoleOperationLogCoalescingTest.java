package com.hengshucredit.rule.server.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.hengshucredit.rule.model.entity.ConsoleOperationLog;
import com.hengshucredit.rule.server.audit.ConsoleOperationLogService;
import com.hengshucredit.rule.server.mapper.ConsoleOperationLogMapper;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public class ConsoleOperationLogCoalescingTest {
    private JdbcTemplate jdbc;
    private ConsoleOperationLogService service;
    private ConsoleOperationLogMapper mapper;
    private TransactionTemplate transaction;

    @Before public void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:operation" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(source);
        String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        for (String table : new String[]{"console_operation_log", "console_operation_log_lock"}) {
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
            jdbc.execute(schema.substring(start, schema.indexOf(';', start) + 1).replaceFirst("(?s)\\) ENGINE=.*;", ")"));
        }
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
        factory.setGlobalConfig(new GlobalConfig().setBanner(false));
        var plugins = new MybatisPlusInterceptor(); plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        factory.setPlugins(plugins);
        var sessions = factory.getObject(); sessions.getConfiguration().addMapper(ConsoleOperationLogMapper.class);
        mapper = new SqlSessionTemplate(sessions).getMapper(ConsoleOperationLogMapper.class);
        service = new ConsoleOperationLogService(mapper);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @After public void cleanup() { if (jdbc != null) jdbc.execute("SHUTDOWN"); }

    private ConsoleOperationLog event(long user, String summary, int second) {
        var log = new ConsoleOperationLog();
        log.setUserId(user); log.setUsername("account-" + user); log.setModule("account"); log.setAction("READ");
        log.setRequestMethod("GET"); log.setRequestPath("/api/rule/console/accounts"); log.setPagePath("/account");
        log.setTargetType("ACCOUNT"); log.setTargetId("8"); log.setTargetName("账户");
        log.setOutcome("SUCCESS"); log.setResultCode(200); log.setChangeSummary(summary); log.setClientIp("127.0.0.1");
        log.setCreateTime(LocalDateTime.of(2026,11,4,17,37,second));
        log.setLeaveTime(log.getCreateTime().plusNanos(20_000_000)); log.setDurationMs(20L);
        return log;
    }
    private void append(ConsoleOperationLog log) { transaction.executeWithoutResult(status -> service.append(log)); }

    @Test public void identicalConsecutiveRecordsKeepFirstEntryAndLastExit() {
        var first = event(8, "查看账户", 1); var last = event(8, "查看账户", 5);
        append(first); append(event(8, "查看账户", 3)); append(last);
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT COUNT(*) FROM console_operation_log", Integer.class));
        var stored = mapper.selectById(first.getId());
        assertEquals(first.getCreateTime(), stored.getCreateTime());
        assertEquals(last.getLeaveTime(), stored.getLeaveTime());
        assertEquals(Long.valueOf(60), stored.getDurationMs());
    }

    @Test public void interveningDifferentOperationStartsANewRecord() {
        append(event(8, "查看账户", 1)); append(event(8, "查看角色", 2)); append(event(8, "查看账户", 3));
        assertEquals(Integer.valueOf(3), jdbc.queryForObject("SELECT COUNT(*) FROM console_operation_log", Integer.class));
    }

    @Test public void eachAccountHasItsOwnSequence() {
        append(event(8, "查看账户", 1)); append(event(9, "查看账户", 2)); append(event(8, "查看账户", 3));
        assertEquals(Integer.valueOf(2), jdbc.queryForObject("SELECT COUNT(*) FROM console_operation_log", Integer.class));
        assertEquals(Long.valueOf(40), jdbc.queryForObject("SELECT duration_ms FROM console_operation_log WHERE user_id=8", Long.class));
        assertEquals(Long.valueOf(20), jdbc.queryForObject("SELECT duration_ms FROM console_operation_log WHERE user_id=9", Long.class));
    }

    @Test public void anyContentDifferenceKeepsASeparateRecord() {
        List<Consumer<ConsoleOperationLog>> changes = List.of(
                log -> log.setUsername("renamed"), log -> log.setModule("rule"), log -> log.setAction("UPDATE"),
                log -> log.setRequestMethod("POST"), log -> log.setRequestPath("/api/rule/console/roles"),
                log -> log.setPagePath("/account?focus=roles"), log -> log.setTargetType("ROLE"),
                log -> log.setTargetId("9"), log -> log.setTargetName("角色"), log -> log.setTargetVersion("v2"),
                log -> log.setOutcome("FAILED"), log -> log.setResultCode(403),
                log -> log.setChangeSummary("查看角色"), log -> log.setClientIp("127.0.0.2"));
        for (Consumer<ConsoleOperationLog> change : changes) {
            jdbc.update("DELETE FROM console_operation_log");
            var first = event(8, "查看账户", 1); var next = event(8, "查看账户", 2); change.accept(next);
            append(first); append(next);
            assertNotEquals(first.getId(), next.getId());
            assertEquals(Integer.valueOf(2), jdbc.queryForObject("SELECT COUNT(*) FROM console_operation_log", Integer.class));
        }
    }

    @Test public void missingAccountIdentityIsNeverCoalesced() {
        var first = event(8, "查看账户", 1); var next = event(8, "查看账户", 2);
        first.setUserId(null); next.setUserId(null); append(first); append(next);
        assertNotEquals(first.getId(), next.getId());
    }

    @Test public void parallelInstancesKeepOneIntervalWithoutLosingUpdates() throws Exception {
        var workers = Executors.newFixedThreadPool(8);
        var ready = new CountDownLatch(8); var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < 32; i++) {
                var log = event(8, "查看账户", i);
                var instance = new ConsoleOperationLogService(mapper);
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试未开始");
                    transaction.executeWithoutResult(status -> instance.append(log));
                    return null;
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
            assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT COUNT(*) FROM console_operation_log", Integer.class));
            var stored = mapper.latestForAppend(8L);
            assertEquals(event(8, "查看账户", 0).getCreateTime(), stored.getCreateTime());
            assertEquals(event(8, "查看账户", 31).getLeaveTime(), stored.getLeaveTime());
            assertEquals(Long.valueOf(640), stored.getDurationMs());
        } finally { start.countDown(); workers.shutdownNow(); workers.awaitTermination(10, TimeUnit.SECONDS); }
    }

    @Test public void internalUpdateCannotExtendAnotherAccountsRecord() {
        var log = event(8, "查看账户", 1); append(log);
        assertNull(mapper.latestForAppend(9L));
        assertEquals(0, mapper.extendForAppend(log.getId(), 9L, log.getCreateTime(), log.getLeaveTime().plusMinutes(1), 20L));
        assertEquals(log.getLeaveTime(), mapper.selectById(log.getId()).getLeaveTime());
    }

    @Test public void listAndDetailExposeStoredIntervalAndFilterByEntryTime() {
        var log = event(8, "查看账户", 1); append(log); append(event(8, "查看账户", 5));
        var page = service.page(1, 20, null, null, null, null, null, null, null);
        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals(event(8, "查看账户", 5).getLeaveTime(), page.getRecords().get(0).getLeaveTime());
        assertEquals(page.getRecords().get(0).getLeaveTime(), service.detail(log.getId()).getLeaveTime());
        assertTrue(service.page(1, 20, null, null, null, null, null, log.getCreateTime().plusSeconds(1), null).getRecords().isEmpty());
    }
}
