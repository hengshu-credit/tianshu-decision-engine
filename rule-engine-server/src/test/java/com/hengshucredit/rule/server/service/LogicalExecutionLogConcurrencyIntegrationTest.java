package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.config.MybatisPlusConfig;
import com.hengshucredit.rule.server.dashboard.DashboardCriteria;
import com.hengshucredit.rule.server.dashboard.DashboardMapping;
import com.hengshucredit.rule.server.dashboard.DashboardQueryRepository;
import com.hengshucredit.rule.server.derived.ExecutionLogHistoryRepository;
import com.hengshucredit.rule.server.mapper.RuleExecutionLogMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Assume;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/** 根 .env 的本机 MySQL，真实分区表、独立事务和并发连接；仅清理本测试创建的 trace。 */
public class LogicalExecutionLogConcurrencyIntegrationTest {
    private static final int WRITERS = 4;

    @Test(timeout = 60000)
    public void concurrentFirstReportsCreateOnlyOnePhysicalRootLog() throws Exception {
        verifyConcurrentWrites(false);
    }

    @Test(timeout = 60000)
    public void concurrentOlderAttemptsCannotOverwriteTheLatestAttempt() throws Exception {
        verifyConcurrentWrites(true);
    }

    @Test(timeout = 60000)
    public void readCommittedParentsSerializeConcurrentFirstReports() throws Exception {
        verifyReadCommittedConcurrentWrites(false);
    }

    @Test(timeout = 60000)
    public void readCommittedParentsPreserveHighestAttemptAgainstWaitingOlderReports() throws Exception {
        verifyReadCommittedConcurrentWrites(true);
    }

    private void verifyReadCommittedConcurrentWrites(boolean seeded) throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("executionLog.integration"));
        DataSource source = localDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        prepareIdentitySchemaWhenPresent(jdbc);
        RuleExecutionLogMapper mapper = mapper(source);
        String trace = UUID.randomUUID().toString();
        CountDownLatch parentsReady = new CountDownLatch(WRITERS);
        CountDownLatch highestLocked = new CountDownLatch(1);
        CountDownLatch olderArrived = new CountDownLatch(WRITERS - 1);
        ExecutorService workers = Executors.newFixedThreadPool(WRITERS);
        try {
            if (seeded) assertEquals(1, mapper.insert(log(trace, 1)));
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < WRITERS; index++) {
                int attempt = index + 2;
                futures.add(workers.submit(() -> {
                    readCommittedParent(source).executeWithoutResult(status -> {
                        Connection parent = assertReadCommittedParent(source, jdbc);
                        parentsReady.countDown();
                        await(parentsReady, "所有RC父事务应先就绪");
                        RuleExecutionLogMapper participating = observeParentConnection(source, parent, mapper);
                        RuleExecutionLogMapper ordered = (RuleExecutionLogMapper) Proxy.newProxyInstance(
                                RuleExecutionLogMapper.class.getClassLoader(), new Class<?>[]{RuleExecutionLogMapper.class},
                                (proxy, method, args) -> {
                                    boolean identityLock = "lockLogicalIdentity".equals(method.getName());
                                    boolean highest = attempt == WRITERS + 1;
                                    if (identityLock && !highest) {
                                        await(highestLocked, "最高attempt应先取得身份锁");
                                        olderArrived.countDown();
                                    }
                                    Object result;
                                    try { result = method.invoke(participating, args); }
                                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                                    if (identityLock && highest) {
                                        highestLocked.countDown();
                                        // 旧尝试都进入同trace写入后再允许新结果提交，避免靠调度运气验证降级保护。
                                        await(olderArrived, "所有旧attempt应在最高attempt提交前进入写入");
                                    }
                                    return result;
                                });
                        service(source, ordered).saveLogical(log(trace, attempt));
                    });
                    return null;
                }));
            }
            for (Future<?> future : futures) future.get(25, TimeUnit.SECONDS);
            assertEquals("RC父事务下同trace仍只能有一条物理根日志", Integer.valueOf(1), jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rule_execution_log WHERE trace_id = ?", Integer.class, trace));
            assertEquals("等待中的旧attempt不能覆盖先提交的新attempt", Integer.valueOf(WRITERS + 1), jdbc.queryForObject(
                    "SELECT attempt_no FROM rule_execution_log WHERE trace_id = ?", Integer.class, trace));
            assertEquals("{\"attempt\":" + (WRITERS + 1) + "}", jdbc.queryForObject(
                    "SELECT output_result FROM rule_execution_log WHERE trace_id = ?", String.class, trace));
            assertEquals("身份指针必须与唯一业务行一致", Integer.valueOf(1), jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rule_execution_log_identity i JOIN rule_execution_log l "
                            + "ON l.id = i.log_id AND l.create_time = i.log_create_time "
                            + "WHERE i.trace_id = ? AND l.trace_id = ?", Integer.class, trace, trace));
        } finally {
            highestLocked.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS));
            deleteOwnTrace(jdbc, trace);
        }
    }

    @Test
    public void readCommittedParentRollbackRemovesBothIdentityAndAuditRow() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("executionLog.integration"));
        DataSource source = localDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        prepareIdentitySchemaWhenPresent(jdbc);
        RuleExecutionLogMapper mapper = mapper(source);
        String trace = UUID.randomUUID().toString();
        try {
            readCommittedParent(source).executeWithoutResult(status -> {
                Connection parent = assertReadCommittedParent(source, jdbc);
                service(source, observeParentConnection(source, parent, mapper)).saveLogical(log(trace, 2));
                assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                        "SELECT COUNT(*) FROM rule_execution_log WHERE trace_id = ?", Integer.class, trace));
                assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                        "SELECT COUNT(*) FROM rule_execution_log_identity WHERE trace_id = ?", Integer.class, trace));
                status.setRollbackOnly();
            });
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("REQUIRED日志写入必须随RC父事务回滚", Integer.valueOf(0), jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rule_execution_log WHERE trace_id = ?", Integer.class, trace));
            assertEquals("身份指针不能在父事务回滚后残留", Integer.valueOf(0), jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rule_execution_log_identity WHERE trace_id = ?", Integer.class, trace));
        } finally {
            deleteOwnTrace(jdbc, trace);
        }
    }

    @Test(timeout = 60000)
    public void readCommittedAdoptionInvalidatesCachedMissingLegacyResult() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("executionLog.integration"));
        DataSource source = localDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        prepareIdentitySchemaWhenPresent(jdbc);
        RuleExecutionLogMapper mapper = mapper(source);
        String trace = UUID.randomUUID().toString();
        RuleExecutionLog higher = log(trace, 7);
        ExecutorService otherConnection = Executors.newSingleThreadExecutor();
        try {
            readCommittedParent(source).executeWithoutResult(status -> {
                Connection parent = assertReadCommittedParent(source, jdbc);
                assertNull(mapper.selectLogicalIdentity(trace));
                assertNull(mapper.selectLegacyLogicalRecord(trace));
                Future<?> committedLegacy = otherConnection.submit(() -> {
                    assertFalse("历史行必须通过父事务之外的独立连接提交",
                            TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(1, mapper.insert(higher));
                });
                try { committedLegacy.get(10, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new AssertionError("独立连接提交历史高attempt失败", failure); }
                assertEquals("RC普通读取应已能看到另一连接的提交", Integer.valueOf(1), jdbc.queryForObject(
                        "SELECT COUNT(*) FROM rule_execution_log WHERE trace_id = ?", Integer.class, trace));
                assertNull("先确认MyBatis仍缓存了之前的空结果，避免用未命中缓存的场景假验证",
                        mapper.selectLegacyLogicalRecord(trace));
                service(source, observeParentConnection(source, parent, mapper)).saveLogical(log(trace, 2));
                assertEquals(higher.getId(), mapper.selectLogicalIdentity(trace).logId());
            });
            assertEquals("接管已提交的历史行，不能因缓存空结果再插一条", Integer.valueOf(1), jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rule_execution_log WHERE trace_id = ?", Integer.class, trace));
            assertEquals(Integer.valueOf(7), jdbc.queryForObject(
                    "SELECT attempt_no FROM rule_execution_log WHERE trace_id = ?", Integer.class, trace));
            assertEquals("{\"attempt\":7}", jdbc.queryForObject(
                    "SELECT output_result FROM rule_execution_log WHERE trace_id = ?", String.class, trace));
        } finally {
            otherConnection.shutdownNow();
            assertTrue(otherConnection.awaitTermination(20, TimeUnit.SECONDS));
            deleteOwnTrace(jdbc, trace);
        }
    }

    private static TransactionTemplate readCommittedParent(DataSource source) {
        TransactionTemplate parent = new TransactionTemplate(new DataSourceTransactionManager(source));
        parent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        parent.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return parent;
    }

    private static Connection assertReadCommittedParent(DataSource source, JdbcTemplate jdbc) {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals("READ-COMMITTED", jdbc.queryForObject("SELECT @@transaction_isolation", String.class));
        Connection connection = DataSourceUtils.getConnection(source);
        try {
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.getTransactionIsolation());
            assertFalse(connection.getAutoCommit());
            return connection;
        } catch (SQLException failure) {
            throw new AssertionError("无法验证RC父事务的实际连接状态", failure);
        } finally {
            DataSourceUtils.releaseConnection(connection, source);
        }
    }

    private static RuleExecutionLogMapper observeParentConnection(DataSource source, Connection parent,
            RuleExecutionLogMapper delegate) {
        return (RuleExecutionLogMapper) Proxy.newProxyInstance(RuleExecutionLogMapper.class.getClassLoader(),
                new Class<?>[]{RuleExecutionLogMapper.class}, (proxy, method, args) -> {
                    Connection connection = DataSourceUtils.getConnection(source);
                    try { assertSame("saveLogical REQUIRED必须复用RC父事务连接", parent, connection); }
                    finally { DataSourceUtils.releaseConnection(connection, source); }
                    try { return method.invoke(delegate, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
    }

    private static void await(CountDownLatch latch, String message) {
        try { assertTrue(message, latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(message, failure);
        }
    }

    private void verifyConcurrentWrites(boolean seeded) throws Exception {
        Assume.assumeTrue("设置 -DexecutionLog.integration=true 后运行真实 MySQL 并发验证",
                Boolean.getBoolean("executionLog.integration"));
        DataSource dataSource = localDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        prepareIdentitySchemaWhenPresent(jdbc);
        RuleExecutionLogMapper mapper = mapper(dataSource);
        String trace = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch oldReads = new CountDownLatch(WRITERS);
        CountDownLatch highestFinished = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(WRITERS);
        try {
            if (seeded) assertEquals(1, mapper.insert(log(trace, 1)));
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < WRITERS; index++) {
                int attempt = index + 2;
                RuleExecutionLogService service = service(dataSource,
                        observeUnprotectedReads(mapper, oldReads, highestFinished, attempt == WRITERS + 1));
                futures.add(workers.submit(() -> {
                    try {
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        service.saveLogical(log(trace, attempt));
                    } finally {
                        if (attempt == WRITERS + 1) highestFinished.countDown();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(25, TimeUnit.SECONDS);
            assertEquals("同一 trace 首次并发不能插入多条根日志", Integer.valueOf(1),
                    jdbc.queryForObject("SELECT COUNT(*) FROM rule_execution_log WHERE trace_id = ?", Integer.class, trace));
            assertEquals("较旧尝试不能在新尝试提交后降级结果", Integer.valueOf(WRITERS + 1),
                    jdbc.queryForObject("SELECT attempt_no FROM rule_execution_log WHERE trace_id = ?", Integer.class, trace));
            assertEquals("{\"attempt\":" + (WRITERS + 1) + "}",
                    jdbc.queryForObject("SELECT output_result FROM rule_execution_log WHERE trace_id = ?", String.class, trace));
        } finally {
            highestFinished.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS));
            deleteOwnTrace(jdbc, trace);
        }
    }

    @Test
    public void unversionedReplayCannotReplaceARecoveredAttempt() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("executionLog.integration"));
        DataSource source = localDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        prepareIdentitySchemaWhenPresent(jdbc);
        RuleExecutionLogMapper mapper = mapper(source);
        String trace = UUID.randomUUID().toString();
        try {
            assertEquals(1, mapper.insert(log(trace, 4)));
            RuleExecutionLog replay = log(trace, 1);
            replay.setAttemptNo(null);
            service(source, mapper).saveLogical(replay);
            assertEquals("没有 attempt 的旧客户端上报仅代表初次尝试", Integer.valueOf(4),
                    jdbc.queryForObject("SELECT attempt_no FROM rule_execution_log WHERE trace_id = ?", Integer.class, trace));
            assertEquals("{\"attempt\":4}", jdbc.queryForObject(
                    "SELECT output_result FROM rule_execution_log WHERE trace_id = ?", String.class, trace));
        } finally {
            deleteOwnTrace(jdbc, trace);
        }
    }

    @Test
    public void adoptsHighestLegacyAttemptWithoutDeletingHistoricalRowsOrMovingItsPartition() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("executionLog.integration"));
        DataSource source = localDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        prepareIdentitySchemaWhenPresent(jdbc);
        RuleExecutionLogMapper mapper = mapper(source);
        String trace = UUID.randomUUID().toString();
        try {
            RuleExecutionLog higher = log(trace, 5);
            higher.setCreateTime(LocalDateTime.of(2026, 1, 15, 12, 0));
            RuleExecutionLog lower = log(trace, 2);
            lower.setCreateTime(LocalDateTime.of(2026, 2, 15, 12, 0));
            assertEquals(1, mapper.insert(higher));
            assertEquals(1, mapper.insert(lower));
            RuleExecutionLog next = log(trace, 6);
            next.setCreateTime(LocalDateTime.of(2026, 3, 15, 12, 0));
            service(source, mapper).saveLogical(next);
            assertEquals("已存在的旧审计行必须保留", Integer.valueOf(2), jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rule_execution_log WHERE trace_id = ?", Integer.class, trace));
            assertEquals("应更新最高尝试的代表行，而不是仅按时间选择较旧结果", Integer.valueOf(6),
                    jdbc.queryForObject("SELECT attempt_no FROM rule_execution_log WHERE id = ? AND create_time = ?",
                            Integer.class, higher.getId(), higher.getCreateTime()));
            assertEquals("较低尝试的历史原始记录不得重写", Integer.valueOf(2),
                    jdbc.queryForObject("SELECT attempt_no FROM rule_execution_log WHERE id = ? AND create_time = ?",
                            Integer.class, lower.getId(), lower.getCreateTime()));
        } finally {
            deleteOwnTrace(jdbc, trace);
        }
    }

    @Test
    public void allBusinessReadersShareOneLogicalResultBeforeAndAfterBoundedBackfill() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("executionLog.integration"));
        DataSource source = localDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        prepareIdentitySchemaWhenPresent(jdbc);
        RuleExecutionLogMapper mapper = mapper(source);
        RuleExecutionLogService logs = service(source, mapper);
        String trace = UUID.randomUUID().toString();
        String projectCode = "logical_" + trace;
        long projectId = 9_000_000_000L + Math.abs((long) trace.hashCode());
        LocalDateTime now = LocalDateTime.now().withNano(0);
        try {
            RuleExecutionLog higher = log(trace, 3);
            higher.setCreateTime(now.minusMinutes(2));
            higher.setStartedAt(higher.getCreateTime());
            higher.setSource("CLIENT_SERVER");
            higher.setExecutionProjectId(projectId);
            higher.setRootRuleId(projectId);
            higher.setProjectCode(projectCode);
            higher.setHistoryFields("{\"version\":1,\"fields\":{\"VARIABLE:12\":42},\"apiIds\":[]}");
            higher.setTraceInfo("[{\"modelType\":\"RULE_SET\",\"ruleId\":" + projectId
                    + ",\"status\":\"SUCCESS\",\"events\":[{\"type\":\"RULE_SET_SUMMARY\",\"evaluated\":true,\"hit\":true}]}]");
            higher.setExecuteTimeMs(22L);
            RuleSetTraceSummary summary = RuleSetTraceSummary.fromTrace(higher.getTraceInfo());
            higher.setRuleSetStatsJson(summary.json()); higher.setRuleSetStatsStatus(summary.status());
            RuleExecutionLog lower = com.alibaba.fastjson.JSON.parseObject(
                    com.alibaba.fastjson.JSON.toJSONString(higher), RuleExecutionLog.class);
            lower.setAttemptNo(1);
            lower.setCreateTime(now.minusMinutes(1));
            lower.setStartedAt(lower.getCreateTime());
            lower.setExecuteTimeMs(11L);
            lower.setHistoryFields("{\"version\":1,\"fields\":{\"VARIABLE:12\":7},\"apiIds\":[]}");
            assertEquals(1, mapper.insert(higher));
            assertEquals(1, mapper.insert(lower));
            RuleSetStatisticsStore statisticsStore = new RuleSetStatisticsStore(jdbc);
            statisticsStore.persist(higher.getId(), higher.getCreateTime(), summary.json(), summary.status());
            statisticsStore.persist(lower.getId(), lower.getCreateTime(), summary.json(), summary.status());
            jdbc.update("UPDATE rule_execution_log SET rule_set_stats_version=2 WHERE trace_id=?", trace);
            DashboardCriteria criteria = new DashboardCriteria(projectId, projectCode, now.minusHours(1), now.plusMinutes(1));
            DashboardQueryRepository dashboard = new DashboardQueryRepository(jdbc);
            ExecutionLogHistoryRepository history = new ExecutionLogHistoryRepository(jdbc);
            ProjectWorkbenchService workbench = new ProjectWorkbenchService();
            RuleProject project = new RuleProject();
            project.setId(projectId);
            project.setProjectCode(projectCode);
            project.setStatus(1);
            RuleProjectMapper projects = (RuleProjectMapper) Proxy.newProxyInstance(RuleProjectMapper.class.getClassLoader(),
                    new Class<?>[]{RuleProjectMapper.class}, (proxy, method, args) -> "selectById".equals(method.getName()) ? project : null);
            ReflectionTestUtils.setField(workbench, "projectMapper", projects);
            ReflectionTestUtils.setField(workbench, "executionLogMapper", mapper);
            for (int phase = 0; phase < 2; phase++) {
                assertEquals("原始审计行不删不改", Integer.valueOf(2), jdbc.queryForObject(
                        "SELECT COUNT(*) FROM rule_execution_log WHERE trace_id = ?", Integer.class, trace));
                var page = logs.pageList(1, 10, null, null, null, null, null, trace,
                        null, null, null, null, null);
                assertEquals(1, page.getTotal());
                assertEquals(higher.getId(), page.getRecords().get(0).getId());
                assertEquals(lower.getId(), logs.getById(lower.getId()).getId());
                Map<?, ?> overview = (Map<?, ?>) logs.ruleSetStats(projectCode, null, null,
                        now.minusHours(1), now.plusMinutes(1)).get("overview");
                assertEquals(1L, overview.get("evaluationCount"));
                assertEquals(List.of(22L), dashboard.executionDurations(criteria));
                var applications = dashboard.applicationRows(criteria, new DashboardMapping.ResolvedMappings(null));
                assertEquals(1, applications.size());
                assertEquals(higher.getId(), applications.get(0).id());
                var rows = history.query("RULE", projectId, projectId, now.minusHours(1), now.plusMinutes(1), Map.of());
                assertEquals(1, rows.size());
                assertEquals(42, rows.get(0).fields().get("VARIABLE:12"));
                var metrics = workbench.getWorkbench(projectId).getMetrics();
                assertEquals(Long.valueOf(1), metrics.getRecentExecutionCount());
                assertEquals(Long.valueOf(1), metrics.getRecentSuccessCount());
                var tracePlan = jdbc.queryForList("EXPLAIN SELECT * FROM rule_execution_log_current WHERE trace_id = ?", trace);
                var historyPlan = jdbc.queryForList("EXPLAIN SELECT id, trace_id, started_at, history_fields "
                                + "FROM rule_execution_log_current WHERE started_at >= ? AND started_at < ? "
                                + "AND success = 1 AND source = 'CLIENT_SERVER' AND history_fields IS NOT NULL "
                                + "AND root_rule_id = ? AND execution_project_id = ? ORDER BY started_at, id LIMIT 100001",
                        now.minusHours(1), now.plusMinutes(1), projectId, projectId);
                assertFalse(tracePlan.isEmpty());
                assertFalse(historyPlan.isEmpty());
                System.out.println("N26_EXPLAIN_TRACE_PHASE_" + phase + '=' + com.alibaba.fastjson.JSON.toJSONString(tracePlan));
                System.out.println("N26_EXPLAIN_HISTORY_PHASE_" + phase + '=' + com.alibaba.fastjson.JSON.toJSONString(historyPlan));
                if (phase == 0) backfillOnlyOwnTrace(source, jdbc, trace);
            }
            RuleExecutionLog failed = log(trace, 4);
            failed.setSuccess(0);
            failed.setOutputResult(null);
            failed.setErrorMessage("newer execution failed");
            logs.saveLogical(failed);
            assertNull("新失败结果不能保留上一尝试的成功输出", logs.getById(higher.getId()).getOutputResult());
            assertTrue("成功过滤必须在逻辑代表行判定之后，不能回退到旧成功记录",
                    history.query("RULE", projectId, projectId, now.minusHours(1), now.plusMinutes(1), Map.of()).isEmpty());
            assertFalse("EXPLAIN应可检查该统一视图的访问路径", jdbc.queryForList(
                    "EXPLAIN SELECT * FROM rule_execution_log_current WHERE trace_id = ?", trace).isEmpty());
        } finally {
            deleteOwnTrace(jdbc, trace);
        }
    }

    @Test
    public void batchFailureRollsBackBothLogicalIdentitiesAndAuditRows() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("executionLog.integration"));
        DataSource source = localDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        prepareIdentitySchemaWhenPresent(jdbc);
        RuleExecutionLogMapper delegate = mapper(source);
        String suffix = UUID.randomUUID().toString().substring(1);
        String first = 'a' + suffix;
        String second = 'b' + suffix;
        RuleExecutionLogMapper failing = (RuleExecutionLogMapper) Proxy.newProxyInstance(RuleExecutionLogMapper.class.getClassLoader(),
                new Class<?>[]{RuleExecutionLogMapper.class}, (proxy, method, args) -> {
                    if ("insert".equals(method.getName()) && second.equals(((RuleExecutionLog) args[0]).getTraceId())) {
                        throw new IllegalStateException("controlled second log failure");
                    }
                    try { return method.invoke(delegate, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
        try {
            assertThrows(IllegalStateException.class,
                    () -> service(source, failing).saveLogicalBatch(List.of(log(second, 1), log(first, 1))));
            assertEquals(Integer.valueOf(0), jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rule_execution_log WHERE trace_id IN (?, ?)", Integer.class, first, second));
            assertEquals(Integer.valueOf(0), jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rule_execution_log_identity WHERE trace_id IN (?, ?)", Integer.class, first, second));
        } finally {
            deleteOwnTrace(jdbc, first);
            deleteOwnTrace(jdbc, second);
        }
    }

    @Test
    public void legacyBootstrapDefersTheReadViewUntilItsAttemptColumnExists() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("executionLog.integration"));
        DataSource source = localDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String audit = "n26_upgrade_log_" + suffix;
        String identity = "n26_upgrade_identity_" + suffix;
        String view = "n26_upgrade_view_" + suffix;
        try {
            jdbc.execute("CREATE TABLE `" + audit + "` (id BIGINT NOT NULL, trace_id CHAR(36), "
                    + "create_time DATETIME NOT NULL, PRIMARY KEY(id, create_time)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
            Matcher identityDdl = Pattern.compile("CREATE TABLE IF NOT EXISTS `rule_execution_log_identity`[\\s\\S]*?;").matcher(schema);
            assertTrue(identityDdl.find());
            String initialization = identityDdl.group();
            Matcher prematureView = Pattern.compile("CREATE OR REPLACE SQL SECURITY INVOKER VIEW `rule_execution_log_current`[\\s\\S]*?;").matcher(schema);
            if (prematureView.find()) initialization += '\n' + prematureView.group();
            try (var connection = source.getConnection()) {
                // 与Spring SQL initialization相同，任何DDL错误立即失败，不能靠continue-on-error放过旧库。
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(renameLogTables(
                        initialization, audit, identity, view).getBytes(StandardCharsets.UTF_8)));
            }
            assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'attempt_no'", Integer.class, audit));
            jdbc.execute("ALTER TABLE `" + audit + "` ADD COLUMN attempt_no INT DEFAULT NULL");
            String deferredView = new ClassPathResource("sql/logical-execution-log-view.sql").getContentAsString(StandardCharsets.UTF_8);
            try (var connection = source.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(renameLogTables(
                        deferredView, audit, identity, view).getBytes(StandardCharsets.UTF_8)));
            }
            String trace = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO `" + audit + "` (id, trace_id, create_time, attempt_no) VALUES (1, ?, '2026-01-15', 2), (2, ?, '2026-02-15', 1)", trace, trace);
            assertEquals(Integer.valueOf(2), jdbc.queryForObject("SELECT COUNT(*) FROM `" + audit + "`", Integer.class));
            assertEquals(Long.valueOf(1), jdbc.queryForObject("SELECT id FROM `" + view + "`", Long.class));
        } finally {
            // 仅删除本方法创建的UUID隔离对象，绝不更改现有rule_execution_log的列或历史数据。
            jdbc.execute("DROP VIEW IF EXISTS `" + view + "`");
            jdbc.execute("DROP TABLE IF EXISTS `" + identity + "`");
            jdbc.execute("DROP TABLE IF EXISTS `" + audit + "`");
        }
    }

    private static String renameLogTables(String sql, String audit, String identity, String view) {
        return sql.replace("rule_execution_log_identity", identity)
                .replace("rule_execution_log_current", view).replace("rule_execution_log", audit);
    }

    private static void backfillOnlyOwnTrace(DataSource source, JdbcTemplate jdbc, String trace) throws Exception {
        String after = trace.substring(0, trace.length() - 1);
        assertEquals("执行维护脚本前确认游标范围内只有本测试 trace", List.of(trace), jdbc.queryForList(
                "SELECT DISTINCT trace_id FROM rule_execution_log WHERE trace_id > ? AND trace_id <= ?", String.class, after, trace));
        try (var connection = source.getConnection(); var statement = connection.prepareStatement(
                "SET @execution_log_after_trace = ?, @execution_log_through_trace = ?")) {
            statement.setString(1, after);
            statement.setString(2, trace);
            statement.execute();
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("sql/backfill-execution-log-identity.sql"));
        }
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "SELECT COUNT(*) FROM rule_execution_log_identity WHERE trace_id = ?", Integer.class, trace));
    }

    private static RuleExecutionLogMapper observeUnprotectedReads(RuleExecutionLogMapper delegate,
            CountDownLatch reads, CountDownLatch highestFinished, boolean highest) {
        return (RuleExecutionLogMapper) Proxy.newProxyInstance(RuleExecutionLogMapper.class.getClassLoader(),
                new Class<?>[]{RuleExecutionLogMapper.class}, (proxy, method, args) -> {
                    Object result;
                    try { result = method.invoke(delegate, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                    // 稳定复现旧实现非事务 SELECT 后 INSERT/UPDATE 的窗口；不阻塞新事务内的行锁查询。
                    if ("selectOne".equals(method.getName()) && !TransactionSynchronizationManager.isActualTransactionActive()) {
                        reads.countDown();
                        assertTrue("并发旧读取未全部到达", reads.await(10, TimeUnit.SECONDS));
                        if (!highest) assertTrue(highestFinished.await(10, TimeUnit.SECONDS));
                    }
                    return result;
                });
    }

    private static RuleExecutionLogService service(DataSource source, RuleExecutionLogMapper mapper) {
        RuleExecutionLogService target = new RuleExecutionLogService();
        ReflectionTestUtils.setField(target, "baseMapper", mapper);
        ReflectionTestUtils.setField(target, "entityClass", RuleExecutionLog.class);
        ReflectionTestUtils.setField(target, "projectFilterService", new ProjectFilterService());
        ReflectionTestUtils.setField(target, "statistics", new RuleSetStatisticsQuery(new JdbcTemplate(source)));
        ReflectionTestUtils.setField(target, "statisticsStore", new RuleSetStatisticsStore(new JdbcTemplate(source)));
        TransactionInterceptor transactions = new TransactionInterceptor();
        transactions.setTransactionManager(new DataSourceTransactionManager(source));
        transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        transactions.afterPropertiesSet();
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(transactions);
        return (RuleExecutionLogService) proxy.getProxy();
    }

    private static RuleExecutionLogMapper mapper(DataSource source) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        factory.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor());
        factory.setTransactionFactory(new SpringManagedTransactionFactory());
        SqlSessionFactory sessions = factory.getObject();
        assertNotNull(sessions);
        sessions.getConfiguration().addMapper(RuleExecutionLogMapper.class);
        return new SqlSessionTemplate(sessions).getMapper(RuleExecutionLogMapper.class);
    }

    private static RuleExecutionLog log(String trace, int attempt) {
        RuleExecutionLog log = new RuleExecutionLog();
        log.setTraceId(trace);
        log.setRuleCode("logical_log_concurrency_test");
        log.setSource("SERVER");
        log.setAttemptNo(attempt);
        log.setSuccess(1);
        log.setOutputResult("{\"attempt\":" + attempt + "}");
        log.setCreateTime(LocalDateTime.now().withNano(0));
        return log;
    }

    private static void prepareIdentitySchemaWhenPresent(JdbcTemplate jdbc) throws Exception {
        // 新增表使用生产 schema 的同一 DDL；旧实现红测阶段尚无该 DDL，不影响竞争复现。
        String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        Matcher table = Pattern.compile("CREATE TABLE IF NOT EXISTS `rule_execution_log_identity`[\\s\\S]*?;").matcher(schema);
        if (table.find()) jdbc.execute(table.group());
        SchemaSyncService migration = new SchemaSyncService();
        ReflectionTestUtils.setField(migration, "jdbcTemplate", jdbc);
        java.lang.reflect.Method statistics = SchemaSyncService.class.getDeclaredMethod("ensureRuleSetStatisticsSchema");
        statistics.setAccessible(true); statistics.invoke(migration);
        jdbc.execute(new ClassPathResource("sql/logical-execution-log-view.sql").getContentAsString(StandardCharsets.UTF_8));
        jdbc.execute(new ClassPathResource("sql/logical-execution-statistics-view.sql").getContentAsString(StandardCharsets.UTF_8));
        assertTrue("必须在真实分区日志表验证，而不是无分区的替代空表", jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.partitions WHERE table_schema = DATABASE() "
                        + "AND table_name = 'rule_execution_log' AND partition_name IS NOT NULL", Integer.class) > 0);
    }

    private static void deleteOwnTrace(JdbcTemplate jdbc, String trace) {
        Integer eventsExist = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='rule_set_statistics_event'", Integer.class);
        if (eventsExist != null && eventsExist > 0) jdbc.update("DELETE e FROM rule_set_statistics_event e JOIN rule_execution_log l ON l.id=e.log_id AND l.create_time=e.create_time WHERE l.trace_id=?", trace);
        Integer statisticsExists = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='rule_execution_statistics'", Integer.class);
        if (statisticsExists != null && statisticsExists > 0) jdbc.update("DELETE FROM rule_execution_statistics WHERE trace_id=?", trace);
        jdbc.update("DELETE FROM rule_execution_log WHERE trace_id = ?", trace);
        Integer identityExists = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = 'rule_execution_log_identity'", Integer.class);
        if (identityExists != null && identityExists > 0) jdbc.update("DELETE FROM rule_execution_log_identity WHERE trace_id = ?", trace);
        assertEquals(Integer.valueOf(0), jdbc.queryForObject(
                "SELECT COUNT(*) FROM rule_execution_log WHERE trace_id = ?", Integer.class, trace));
    }

    private static DataSource localDataSource() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("rule-engine-server"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("无法定位仓库根目录");
        Properties settings = new Properties();
        try (Reader reader = Files.newBufferedReader(root.resolve(".env"), StandardCharsets.UTF_8)) { settings.load(reader); }
        InetAddress address = InetAddress.getByName(required(settings, "MYSQL_HOST"));
        if (!address.isLoopbackAddress()) throw new IllegalStateException("日志并发集成测试仅允许本机 MySQL");
        String database = required(settings, "MYSQL_DATABASE");
        if (!database.matches("[A-Za-z0-9_$-]+")) throw new IllegalStateException("MYSQL_DATABASE 格式无效");
        int port = Integer.parseInt(required(settings, "MYSQL_PORT"));
        if (port < 1 || port > 65535) throw new IllegalStateException("MYSQL_PORT 无效");
        String host = address.getHostAddress();
        if (host.contains(":")) host = '[' + host + ']';
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + host + ':' + port + '/' + database
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=15000");
        dataSource.setUsername(required(settings, "MYSQL_USERNAME"));
        dataSource.setPassword(required(settings, "MYSQL_PASSWORD"));
        return dataSource;
    }

    private static String required(Properties settings, String key) {
        String value = settings.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("根 .env 缺少 " + key);
        return value;
    }
}
