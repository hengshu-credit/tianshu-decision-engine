package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.core.engine.ExecutionCapacityException;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.core.util.MissingValueSemantics;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLDataException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DBConnectPools implements DisposableBean {

    @Resource
    private RuleDbDatasourceMapper datasourceMapper;

    private final ConcurrentMap<Long, PoolSlot> pools = new ConcurrentHashMap<>();
    private final Object poolLifecycle = new Object();
    private volatile boolean closed;
    private final ScheduledThreadPoolExecutor deadlineExecutor;
    private final Semaphore queryPermits;
    private final int queryConcurrencyLimit;
    private final AtomicLong queryRejected = new AtomicLong();

    public DBConnectPools() {
        this(128);
    }

    @Autowired
    public DBConnectPools(@Value("${rule-engine.database.max-concurrent-queries:128}") int maxConcurrentQueries) {
        if (maxConcurrentQueries <= 0) {
            throw new IllegalArgumentException("数据库查询并发上限必须大于 0");
        }
        queryConcurrencyLimit = maxConcurrentQueries;
        queryPermits = new Semaphore(maxConcurrentQueries, true);
        deadlineExecutor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "database-query-deadline");
            thread.setDaemon(true);
            return thread;
        });
        deadlineExecutor.setRemoveOnCancelPolicy(true);
    }

    public HikariDataSource getDataSource(Long datasourceId) {
        if (datasourceId == null) {
            throw new IllegalArgumentException("数据源ID不能为空");
        }
        while (true) {
            ensureOpen();
            PoolSlot slot = pools.get(datasourceId);
            boolean initialize = false;
            if (slot == null) {
                // 这里只登记加载槽位；慢数据库和 SSH 初始化不持有全局锁。
                synchronized (poolLifecycle) {
                    ensureOpen();
                    slot = pools.get(datasourceId);
                    if (slot == null) {
                        slot = new PoolSlot(datasourceId);
                        pools.put(datasourceId, slot);
                        initialize = true;
                    }
                }
            }
            if (initialize) initializePool(datasourceId, slot);
            DbPoolHolder holder = slot.awaitActive();
            if (!holder.isClosed()) return holder.dataSource;
            if (pools.remove(datasourceId, slot)) {
                closeQuietly(slot.retire(new IllegalStateException("数据库连接池已失效")));
            }
        }
    }

    private void initializePool(Long datasourceId, PoolSlot slot) {
        try {
            slot.ensureActive();
            RuleDbDatasource datasource = datasourceMapper.selectById(datasourceId);
            if (datasource == null) throw new IllegalArgumentException("数据库数据源不存在");
            if (datasource.getStatus() == null || datasource.getStatus() != 1) {
                throw new IllegalArgumentException("数据库数据源未启用");
            }
            DbPoolHolder created;
            try {
                slot.ensureActive();
                created = buildPool(datasource);
            } catch (Exception e) {
                throw new IllegalStateException("创建数据库连接池失败：" + e.getMessage(), e);
            }
            if (!slot.publish(created)) {
                closeQuietly(created);
                throw new IllegalStateException("数据库连接池已刷新或关闭，请重试");
            }
        } catch (RuntimeException | Error failure) {
            pools.remove(datasourceId, slot);
            closeQuietly(slot.retire(failure));
            throw failure;
        }
    }

    public void refresh(Long datasourceId) {
        PoolSlot previous;
        synchronized (poolLifecycle) {
            previous = pools.remove(datasourceId);
        }
        if (previous != null) {
            closeQuietly(previous.retire(new IllegalStateException("数据库连接池已刷新，请重试")));
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("数据库连接池已关闭");
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Map<String, Object>> poolDetails = new LinkedHashMap<>();
        long active = 0;
        long idle = 0;
        long total = 0;
        long pending = 0;
        for (Map.Entry<Long, PoolSlot> entry : pools.entrySet()) {
            DbPoolHolder holder = entry.getValue().currentHolder();
            if (holder == null || holder.isClosed()) continue;
            var bean = holder.dataSource.getHikariPoolMXBean();
            if (bean == null) continue;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("active", bean.getActiveConnections());
            detail.put("idle", bean.getIdleConnections());
            detail.put("total", bean.getTotalConnections());
            detail.put("pending", bean.getThreadsAwaitingConnection());
            poolDetails.put(String.valueOf(entry.getKey()), detail);
            active += bean.getActiveConnections();
            idle += bean.getIdleConnections();
            total += bean.getTotalConnections();
            pending += bean.getThreadsAwaitingConnection();
        }
        result.put("poolCount", poolDetails.size());
        result.put("active", active);
        result.put("idle", idle);
        result.put("total", total);
        result.put("pending", pending);
        result.put("queryConcurrencyLimit", queryConcurrencyLimit);
        result.put("queryConcurrencyActive", queryActiveCount());
        result.put("queryRejected", queryRejected.get());
        result.put("pools", poolDetails);
        return result;
    }

    public void testConnection(RuleDbDatasource datasource) throws Exception {
        ensureOpen();
        String validationQuery = hasText(datasource.getValidationQuery())
                ? datasource.getValidationQuery()
                : "SELECT 1";
        String executableValidationQuery = validationSql(validationQuery);
        DbPoolHolder testPool = buildPool(datasource);
        try (Connection connection = testPool.dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(executableValidationQuery)) {
                statement.execute();
            }
        } finally {
            closeQuietly(testPool);
        }
    }

    public List<Map<String, Object>> query(Long datasourceId, String sql, List<Object> params, int maxRows) throws Exception {
        return query(datasourceId, sql, params, maxRows, 0);
    }

    public List<Map<String, Object>> query(Long datasourceId, String sql, List<Object> params,
                                           int maxRows, int queryTimeoutSeconds) throws Exception {
        return queryInternal(datasourceId, sql, params, maxRows, queryTimeoutSeconds, 0);
    }

    public List<Map<String, Object>> query(Long datasourceId, String sql, List<Object> params,
                                           int maxRows, int queryTimeoutSeconds,
                                           int maxContentBytes) throws Exception {
        if (maxContentBytes == 0) {
            return query(datasourceId, sql, params, maxRows, queryTimeoutSeconds);
        }
        return queryInternal(datasourceId, sql, params, maxRows, queryTimeoutSeconds, maxContentBytes);
    }

    private List<Map<String, Object>> queryInternal(Long datasourceId, String sql, List<Object> params,
                                                    int maxRows, int queryTimeoutSeconds,
                                                    int maxContentBytes) throws Exception {
        DatabaseQueryOptions options = new DatabaseQueryOptions(maxRows, queryTimeoutSeconds, maxContentBytes);
        SqlQuerySupport.Analysis analysis = SqlQuerySupport.analyze(sql);
        if (!isReadOnlySelectSql(sql)) {
            throw new IllegalArgumentException("只允许执行 SELECT 查询");
        }
        int actualParams = params == null ? 0 : params.size();
        if (analysis.placeholderCount() != actualParams) {
            throw new IllegalArgumentException("SQL 需要 " + analysis.placeholderCount()
                    + " 个参数，当前配置了 " + actualParams + " 个，请核对参数顺序和数量");
        }
        acquireQueryPermit();
        List<Map<String, Object>> rows = new ArrayList<>();
        long contentBytes = 0L;
        try (QueryDeadline ignored = new QueryDeadline()) {
            HikariDataSource dataSource = getDataSource(datasourceId);
            checkQueryBudget();
            try (Connection connection = dataSource.getConnection();
                 NetworkTimeoutBudget network = new NetworkTimeoutBudget(connection)) {
                network.applyRemaining();
                try (PreparedStatement statement = connection.prepareStatement(SqlQuerySupport.executableSql(sql))) {
                    statement.setMaxRows(options.maxRows());
                    if (params != null) {
                        for (int i = 0; i < params.size(); i++) {
                            checkQueryBudget();
                            statement.setObject(i + 1, MissingValueSemantics.normalize(params.get(i)));
                        }
                    }
                    int remaining = checkQueryBudget();
                    statement.setQueryTimeout(RequestDeadlineContext.capture() == null
                            ? options.queryTimeoutSeconds() : options.timeoutWithinBudget(remaining));
                    network.applyRemaining();
                    try (ResultSet resultSet = statement.executeQuery()) {
                        checkQueryBudget();
                        ResultSetMetaData metaData = resultSet.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        while (true) {
                            network.applyRemaining();
                            boolean hasNext = resultSet.next();
                            checkQueryBudget();
                            if (!hasNext) break;
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= columnCount; i++) {
                                network.applyRemaining();
                                String columnLabel = metaData.getColumnLabel(i);
                                Object value = MissingValueSemantics.normalize(resultSet.getObject(i));
                                contentBytes += contentBytes(columnLabel) + contentBytes(value);
                                if (options.maxContentBytes() > 0 && contentBytes > options.maxContentBytes()) {
                                    throw new SQLDataException("查询结果超过内容大小限制: " + options.maxContentBytes() + " 字节");
                                }
                                row.put(columnLabel, value);
                                checkQueryBudget();
                            }
                            rows.add(row);
                        }
                    }
                }
            }
        } catch (Exception failure) {
            if (RequestDeadlineContext.remainingMillis() == 0 && !(failure instanceof SQLTimeoutException)) {
                throw new SQLTimeoutException("数据库查询超过项目请求剩余时间预算", "HYT00", failure);
            }
            throw failure;
        } finally {
            queryPermits.release();
        }
        checkQueryBudget();
        return rows;
    }

    private void acquireQueryPermit() throws SQLException {
        boolean acquired;
        try {
            int remaining = RequestDeadlineContext.remainingMillis();
            acquired = remaining == Integer.MAX_VALUE
                    ? queryPermits.tryAcquire()
                    : queryPermits.tryAcquire(Math.max(1, remaining), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SQLException("等待数据库查询并发许可时被中断", "HY008", interrupted);
        }
        if (!acquired) {
            queryRejected.incrementAndGet();
            throw new ExecutionCapacityException(ExecutionCapacityException.Resource.DATABASE_QUERY,
                    "数据库查询并发已达上限，请稍后重试");
        }
    }

    private int queryActiveCount() {
        return queryConcurrencyLimit - queryPermits.availablePermits();
    }

    private long contentBytes(Object value) {
        return value == null ? 4L : String.valueOf(value).getBytes(StandardCharsets.UTF_8).length;
    }

    private static int checkQueryBudget() throws SQLException {
        int remaining = RequestDeadlineContext.remainingMillis();
        if (remaining == 0) throw new SQLTimeoutException("数据库查询超过项目请求剩余时间预算", "HYT00");
        if (Thread.currentThread().isInterrupted()) throw new SQLException("数据库查询被中断", "HY008");
        return remaining;
    }

    /** 只中断请求线程以终止 Hikari 的可中断等待，不让 JDBC cancel 阻塞调度线程。 */
    private final class QueryDeadline implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final ScheduledFuture<?> timeoutTask;
        private boolean closed;

        private QueryDeadline() throws SQLException {
            int remaining = checkQueryBudget();
            timeoutTask = RequestDeadlineContext.capture() == null ? null : deadlineExecutor.schedule(() -> {
                synchronized (this) {
                    if (!closed) owner.interrupt();
                }
            }, remaining, TimeUnit.MILLISECONDS);
        }

        @Override
        public synchronized void close() {
            closed = true;
            if (timeoutTask != null) timeoutTask.cancel(false);
        }
    }

    /** JDBC 网络超时可能不被驱动支持；语句超时和总预算检查仍然生效。 */
    private static final class NetworkTimeoutBudget implements AutoCloseable {
        private final Connection connection;
        private int originalTimeout;
        private int currentTimeout;
        private boolean supported;
        private boolean changed;

        private NetworkTimeoutBudget(Connection connection) throws SQLException {
            this.connection = connection;
            checkQueryBudget();
            if (RequestDeadlineContext.capture() != null) {
                try {
                    originalTimeout = connection.getNetworkTimeout();
                    currentTimeout = originalTimeout;
                    supported = true;
                } catch (SQLFeatureNotSupportedException ignored) {
                    // 可选 JDBC 能力，不能因此破坏已有数据库接入。
                }
            }
        }

        private void applyRemaining() throws SQLException {
            int remaining = checkQueryBudget();
            if (!supported) return;
            int timeout = originalTimeout > 0 ? Math.min(originalTimeout, remaining) : remaining;
            if (timeout == currentTimeout) return;
            try {
                connection.setNetworkTimeout(Runnable::run, timeout);
                currentTimeout = timeout;
                changed = true;
            } catch (SQLFeatureNotSupportedException ignored) {
                supported = false;
            }
        }

        @Override
        public void close() throws SQLException {
            if (changed && !connection.isClosed()) connection.setNetworkTimeout(Runnable::run, originalTimeout);
        }
    }

    static boolean isReadOnlySelectSql(String sql) {
        return SqlQuerySupport.isReadOnlySelect(sql);
    }

    static String validationSql(String sql) {
        if (!isReadOnlySelectSql(sql)) {
            throw new IllegalArgumentException("校验 SQL 只允许单条只读 SELECT 查询，请检查配置");
        }
        return SqlQuerySupport.executableSql(sql).trim();
    }

    private DbPoolHolder buildPool(RuleDbDatasource datasource) throws Exception {
        String jdbcUrl = resolveJdbcUrl(datasource);
        Session sshSession = null;
        if (isSshTunnel(datasource)) {
            validateSshTunnel(datasource);
            sshSession = openSshSession(datasource);
            int localPort = sshSession.setPortForwardingL(0, datasource.getHost(), defaultPort(datasource));
            jdbcUrl = buildTunnelJdbcUrl(datasource, localPort);
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("rule-db-" + nullToDefault(datasource.getDatasourceCode(), "draft") + "-" + nullToDefault(datasource.getId(), "0"));
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(datasource.getUsername());
        config.setPassword(datasource.getPassword());
        if (hasText(datasource.getDriverClassName())) {
            config.setDriverClassName(datasource.getDriverClassName());
        }
        config.setMaximumPoolSize(defaultInt(datasource.getMaxPoolSize(), 5));
        config.setMinimumIdle(defaultInt(datasource.getMinIdle(), 1));
        config.setConnectionTimeout(defaultInt(datasource.getConnectionTimeoutMs(), 3000));
        config.setIdleTimeout(defaultInt(datasource.getIdleTimeoutMs(), 600000));
        if (hasText(datasource.getValidationQuery())) {
            config.setConnectionTestQuery(validationSql(datasource.getValidationQuery()));
        }
        try {
            return new DbPoolHolder(new HikariDataSource(config), sshSession);
        } catch (RuntimeException e) {
            closeSessionQuietly(sshSession);
            throw e;
        }
    }

    private String resolveJdbcUrl(RuleDbDatasource datasource) {
        if (hasText(datasource.getJdbcUrl())) {
            return datasource.getJdbcUrl().trim();
        }
        String jdbcUrl = buildJdbcUrl(datasource, datasource.getHost(), defaultPort(datasource));
        if (!hasText(jdbcUrl)) {
            throw new IllegalArgumentException("JDBC URL不能为空");
        }
        return jdbcUrl;
    }

    static String buildJdbcUrl(RuleDbDatasource datasource, String host, int port) {
        if (datasource == null || !hasText(host)) {
            return null;
        }
        String dbType = trimToDefault(datasource.getDbType(), "MYSQL").toUpperCase();
        String databaseName = trimToNull(datasource.getDatabaseName());
        String params = trimToNull(datasource.getJdbcParams());
        if ("MYSQL".equals(dbType)) {
            if (!hasText(databaseName)) return null;
            return appendQuestionParams("jdbc:mysql://" + host + ":" + port + "/" + databaseName, params);
        }
        if ("POSTGRESQL".equals(dbType)) {
            if (!hasText(databaseName)) return null;
            return appendQuestionParams("jdbc:postgresql://" + host + ":" + port + "/" + databaseName, params);
        }
        if ("ORACLE".equals(dbType)) {
            if (!hasText(databaseName)) return null;
            String url = "jdbc:oracle:thin:@//" + host + ":" + port + "/" + databaseName;
            return appendQuestionParams(url, params);
        }
        if ("SQLSERVER".equals(dbType)) {
            StringBuilder sb = new StringBuilder("jdbc:sqlserver://").append(host).append(":").append(port);
            if (hasText(databaseName)) {
                sb.append(";databaseName=").append(databaseName);
            }
            if (hasText(params)) {
                String normalized = params.startsWith(";") ? params.substring(1) : params;
                if (normalized.startsWith("?")) {
                    normalized = normalized.substring(1);
                }
                sb.append(";").append(normalized);
            }
            return sb.toString();
        }
        return null;
    }

    static String buildJdbcUrl(RuleDbDatasource datasource) {
        return datasource == null ? null : buildJdbcUrl(datasource, datasource.getHost(), defaultPort(datasource));
    }

    static String buildTunnelJdbcUrl(RuleDbDatasource datasource, int localPort) {
        String generated = buildJdbcUrl(datasource, "127.0.0.1", localPort);
        if (hasText(generated)) {
            return generated;
        }
        String rewritten = rewriteJdbcHostPort(datasource.getJdbcUrl(), datasource.getDbType(), "127.0.0.1", localPort);
        if (hasText(rewritten)) {
            return rewritten;
        }
        throw new IllegalArgumentException("SSH隧道模式下需填写数据库主机、端口和库名，或使用可解析的MySQL/PostgreSQL/SQLServer JDBC URL");
    }

    static String rewriteJdbcHostPort(String jdbcUrl, String dbType, String host, int port) {
        if (!hasText(jdbcUrl)) {
            return null;
        }
        String type = trimToDefault(dbType, "").toUpperCase();
        if ("MYSQL".equals(type) || "POSTGRESQL".equals(type)) {
            Pattern pattern = Pattern.compile("^(jdbc:(?:mysql|postgresql)://)(\\[[^]]+]|[^:/?]+)(?::\\d+)?(.*)$", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(jdbcUrl);
            return matcher.matches() ? matcher.group(1) + host + ":" + port + matcher.group(3) : null;
        }
        if ("SQLSERVER".equals(type)) {
            Pattern pattern = Pattern.compile("^(jdbc:sqlserver://)(\\[[^]]+]|[^:;]+)(?::\\d+)?(.*)$", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(jdbcUrl);
            return matcher.matches() ? matcher.group(1) + host + ":" + port + matcher.group(3) : null;
        }
        return null;
    }

    private Session openSshSession(RuleDbDatasource datasource) throws Exception {
        JSch jsch = new JSch();
        if (hasText(datasource.getSshPrivateKey())) {
            byte[] privateKey = datasource.getSshPrivateKey().getBytes(StandardCharsets.UTF_8);
            byte[] passphrase = hasText(datasource.getSshPassphrase())
                    ? datasource.getSshPassphrase().getBytes(StandardCharsets.UTF_8)
                    : null;
            jsch.addIdentity("rule-db-" + nullToDefault(datasource.getDatasourceCode(), "draft"), privateKey, null, passphrase);
        }
        Session session = jsch.getSession(datasource.getSshUsername(), datasource.getSshHost(), datasource.getSshPort() == null ? 22 : datasource.getSshPort());
        if (hasText(datasource.getSshPassword())) {
            session.setPassword(datasource.getSshPassword());
        }
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(defaultInt(datasource.getSshTimeoutMs(), 10000));
        return session;
    }

    private void validateSshTunnel(RuleDbDatasource datasource) {
        if (!hasText(datasource.getHost())) {
            throw new IllegalArgumentException("SSH隧道模式下数据库主机不能为空");
        }
        if (!hasText(datasource.getSshHost())) {
            throw new IllegalArgumentException("SSH隧道模式下SSH主机不能为空");
        }
        if (!hasText(datasource.getSshUsername())) {
            throw new IllegalArgumentException("SSH隧道模式下SSH用户名不能为空");
        }
        if (!hasText(datasource.getSshPassword()) && !hasText(datasource.getSshPrivateKey())) {
            throw new IllegalArgumentException("SSH隧道模式下SSH密码或私钥不能为空");
        }
    }

    private boolean isSshTunnel(RuleDbDatasource datasource) {
        return datasource != null && "SSH_TUNNEL".equalsIgnoreCase(trimToDefault(datasource.getConnectionMode(), "DIRECT"));
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    static int defaultPort(RuleDbDatasource datasource) {
        if (datasource != null && datasource.getPort() != null && datasource.getPort() > 0) {
            return datasource.getPort();
        }
        String dbType = datasource == null ? "MYSQL" : trimToDefault(datasource.getDbType(), "MYSQL").toUpperCase();
        if ("POSTGRESQL".equals(dbType)) return 5432;
        if ("ORACLE".equals(dbType)) return 1521;
        if ("SQLSERVER".equals(dbType)) return 1433;
        return 3306;
    }

    private static String appendQuestionParams(String url, String params) {
        if (!hasText(params)) {
            return url;
        }
        String normalized = params.trim();
        if (normalized.startsWith("?") || normalized.startsWith("&")) {
            normalized = normalized.substring(1);
        }
        return url + (url.contains("?") ? "&" : "?") + normalized;
    }

    private static String trimToDefault(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToDefault(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void closeQuietly(DbPoolHolder holder) {
        if (holder == null) {
            return;
        }
        if (holder.dataSource != null && !holder.dataSource.isClosed()) {
            holder.dataSource.close();
        }
        closeSessionQuietly(holder.sshSession);
    }

    private void closeSessionQuietly(Session session) {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    @Override
    public void destroy() {
        List<PoolSlot> retired;
        synchronized (poolLifecycle) {
            if (closed) return;
            closed = true;
            retired = new ArrayList<>(pools.values());
            pools.clear();
        }
        deadlineExecutor.shutdownNow();
        List<DbPoolHolder> holders = new ArrayList<>();
        for (PoolSlot slot : retired) {
            holders.add(slot.retire(new IllegalStateException("数据库连接池已关闭")));
        }
        for (DbPoolHolder holder : holders) closeQuietly(holder);
    }

    private final class PoolSlot {
        private final Long datasourceId;
        private final CompletableFuture<DbPoolHolder> ready = new CompletableFuture<>();
        private DbPoolHolder holder;
        private boolean retired;

        private PoolSlot(Long datasourceId) {
            this.datasourceId = datasourceId;
        }

        private synchronized boolean publish(DbPoolHolder created) {
            if (retired || closed || pools.get(datasourceId) != this) return false;
            holder = created;
            ready.complete(created);
            return true;
        }

        private DbPoolHolder awaitActive() {
            try {
                ready.get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待数据库连接池初始化时被中断", interrupted);
            } catch (ExecutionException failed) {
                Throwable cause = failed.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("创建数据库连接池失败", cause);
            }
            synchronized (this) {
                ensureActive();
                return holder;
            }
        }

        private synchronized void ensureActive() {
            if (retired || closed || pools.get(datasourceId) != this) {
                throw new IllegalStateException("数据库连接池已刷新或关闭，请重试");
            }
        }

        private synchronized DbPoolHolder currentHolder() {
            return retired ? null : holder;
        }

        private synchronized DbPoolHolder retire(Throwable reason) {
            retired = true;
            ready.completeExceptionally(reason);
            DbPoolHolder previous = holder;
            holder = null;
            return previous;
        }
    }

    private static class DbPoolHolder {
        private final HikariDataSource dataSource;
        private final Session sshSession;

        private DbPoolHolder(HikariDataSource dataSource, Session sshSession) {
            this.dataSource = dataSource;
            this.sshSession = sshSession;
        }

        private boolean isClosed() {
            return dataSource == null || dataSource.isClosed() || (sshSession != null && !sshSession.isConnected());
        }
    }
}
