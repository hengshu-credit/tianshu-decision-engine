package com.bjjw.rule.server.service;

import com.bjjw.rule.model.entity.RuleDbDatasource;
import com.bjjw.rule.server.mapper.RuleDbDatasourceMapper;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DBConnectPools implements DisposableBean {

    private static final Pattern SELECT_QUERY_PATTERN = Pattern.compile("^select\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCKING_SELECT_PATTERN = Pattern.compile("\\bfor\\s+update\\b|\\block\\s+in\\s+share\\s+mode\\b", Pattern.CASE_INSENSITIVE);

    @Resource
    private RuleDbDatasourceMapper datasourceMapper;

    private final ConcurrentMap<Long, DbPoolHolder> pools = new ConcurrentHashMap<>();

    public HikariDataSource getDataSource(Long datasourceId) {
        if (datasourceId == null) {
            throw new IllegalArgumentException("数据源ID不能为空");
        }
        DbPoolHolder existing = pools.get(datasourceId);
        if (existing != null && !existing.isClosed()) {
            return existing.dataSource;
        }
        RuleDbDatasource datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("数据库数据源不存在");
        }
        if (datasource.getStatus() == null || datasource.getStatus() != 1) {
            throw new IllegalArgumentException("数据库数据源未启用");
        }
        DbPoolHolder created;
        try {
            created = buildPool(datasource);
        } catch (Exception e) {
            throw new IllegalStateException("创建数据库连接池失败：" + e.getMessage(), e);
        }
        DbPoolHolder previous = pools.put(datasourceId, created);
        closeQuietly(previous);
        return created.dataSource;
    }

    public void refresh(Long datasourceId) {
        DbPoolHolder previous = pools.remove(datasourceId);
        closeQuietly(previous);
    }

    public void testConnection(RuleDbDatasource datasource) throws Exception {
        DbPoolHolder testPool = buildPool(datasource);
        try (Connection connection = testPool.dataSource.getConnection()) {
            String validationQuery = hasText(datasource.getValidationQuery())
                    ? datasource.getValidationQuery()
                    : "SELECT 1";
            try (PreparedStatement statement = connection.prepareStatement(validationQuery)) {
                statement.execute();
            }
        } finally {
            closeQuietly(testPool);
        }
    }

    public List<Map<String, Object>> query(Long datasourceId, String sql, List<Object> params, int maxRows) throws Exception {
        if (!isReadOnlySelectSql(sql)) {
            throw new IllegalArgumentException("只允许执行 SELECT 查询");
        }
        int limit = maxRows <= 0 ? 100 : Math.min(maxRows, 500);
        HikariDataSource dataSource = getDataSource(datasourceId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(limit);
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    statement.setObject(i + 1, params.get(i));
                }
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(metaData.getColumnLabel(i), resultSet.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    static boolean isReadOnlySelectSql(String sql) {
        if (!hasText(sql)) {
            return false;
        }
        String trimmed = sql.trim();
        return SELECT_QUERY_PATTERN.matcher(trimmed).find()
                && trimmed.indexOf(';') < 0
                && !LOCKING_SELECT_PATTERN.matcher(trimmed).find();
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
            config.setConnectionTestQuery(datasource.getValidationQuery());
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
        for (DbPoolHolder holder : pools.values()) {
            closeQuietly(holder);
        }
        pools.clear();
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
