package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DBConnectPoolsTest {

    @Test
    public void queryHonorsConfiguredRowCountAboveFiveHundred() throws Exception {
        java.util.Map<String, Object> settings = new java.util.HashMap<>();
        java.sql.ResultSetMetaData metadata = jdbcProxy(java.sql.ResultSetMetaData.class,
                (name, args) -> "getColumnCount".equals(name) ? 0 : null);
        java.sql.ResultSet rows = jdbcProxy(java.sql.ResultSet.class,
                (name, args) -> "getMetaData".equals(name) ? metadata : "next".equals(name) ? false : null);
        java.sql.PreparedStatement statement = jdbcProxy(java.sql.PreparedStatement.class, (name, args) -> {
            if (name.startsWith("set")) settings.put(name, args[0]);
            return "executeQuery".equals(name) ? rows : null;
        });
        java.sql.Connection connection = jdbcProxy(java.sql.Connection.class,
                (name, args) -> "prepareStatement".equals(name) ? statement : null);
        DBConnectPools pools = new DBConnectPools() {
            @Override
            public com.zaxxer.hikari.HikariDataSource getDataSource(Long id) {
                return new com.zaxxer.hikari.HikariDataSource() {
                    @Override
                    public java.sql.Connection getConnection() { return connection; }
                };
            }
        };

        pools.query(1L, "select 1", java.util.List.of(), 2000);

        assertEquals(2000, settings.get("setMaxRows"));
        assertEquals(5, settings.get("setQueryTimeout"));
        pools.query(1L, "select 1", java.util.List.of(), 0, 12);
        assertEquals(0, settings.get("setMaxRows"));
        assertEquals(12, settings.get("setQueryTimeout"));
        pools.query(1L, "select 1", java.util.List.of(), 2000, 0);
        assertEquals(0, settings.get("setQueryTimeout"));
    }

    @Test
    public void queryOptionsRejectNegativeFractionalAndOverflowValues() {
        for (Object invalid : java.util.List.of(-1, 1.5, "2147483648", "abc")) {
            org.junit.Assert.assertThrows(IllegalArgumentException.class,
                    () -> DatabaseQueryOptions.from(java.util.Map.of("maxRows", invalid), 1));
            org.junit.Assert.assertThrows(IllegalArgumentException.class,
                    () -> DatabaseQueryOptions.from(java.util.Map.of("queryTimeoutSeconds", invalid), 1));
        }
        assertEquals(new DatabaseQueryOptions(0, 0), DatabaseQueryOptions.from(
                java.util.Map.of("maxRows", 0, "queryTimeoutSeconds", 0), 1));
    }

    private <T> T jdbcProxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (proxy, method, args) -> handler.apply(method.getName(), args)));
    }

    @Test
    public void validatesSharedFrontendBackendSqlCases() throws Exception {
        try (var input = getClass().getResourceAsStream("/sql/read-only-cases.json")) {
            var cases = com.alibaba.fastjson.JSON.parseArray(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            for (int i = 0; i < cases.size(); i++) {
                var item = cases.getJSONObject(i);
                assertEquals(item.getString("sql"), item.getBooleanValue("valid"),
                        DBConnectPools.isReadOnlySelectSql(item.getString("sql")));
            }
        }
    }

    @Test
    public void buildMysqlJdbcUrlFromFormFields() {
        RuleDbDatasource datasource = datasource("MYSQL", "10.0.0.8", 3307, "riskdb",
                "useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai");

        String jdbcUrl = DBConnectPools.buildJdbcUrl(datasource);

        assertEquals("jdbc:mysql://10.0.0.8:3307/riskdb?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai", jdbcUrl);
    }

    @Test
    public void buildPostgresqlJdbcUrlUsesDefaultPort() {
        RuleDbDatasource datasource = datasource("POSTGRESQL", "pg.internal", null, "riskdb", "sslmode=require");

        String jdbcUrl = DBConnectPools.buildJdbcUrl(datasource);

        assertEquals("jdbc:postgresql://pg.internal:5432/riskdb?sslmode=require", jdbcUrl);
    }

    @Test
    public void buildSqlServerJdbcUrlUsesSemicolonParams() {
        RuleDbDatasource datasource = datasource("SQLSERVER", "sql.internal", 1434, "riskdb", "encrypt=false;trustServerCertificate=true");

        String jdbcUrl = DBConnectPools.buildJdbcUrl(datasource);

        assertEquals("jdbc:sqlserver://sql.internal:1434;databaseName=riskdb;encrypt=false;trustServerCertificate=true", jdbcUrl);
    }

    @Test
    public void rewriteMysqlJdbcUrlForSshTunnelKeepsDatabaseAndParams() {
        String rewritten = DBConnectPools.rewriteJdbcHostPort(
                "jdbc:mysql://mysql.internal:3306/riskdb?useSSL=false&serverTimezone=Asia/Shanghai",
                "MYSQL",
                "127.0.0.1",
                45123);

        assertEquals("jdbc:mysql://127.0.0.1:45123/riskdb?useSSL=false&serverTimezone=Asia/Shanghai", rewritten);
    }

    @Test
    public void readOnlySelectValidationRejectsUnsafeSql() {
        assertTrue(DBConnectPools.isReadOnlySelectSql(" select score from risk_result where id = ?"));
        assertFalse(DBConnectPools.isReadOnlySelectSql("update risk_result set score = 1"));
        assertFalse(DBConnectPools.isReadOnlySelectSql("select score from risk_result; delete from risk_result"));
        assertFalse(DBConnectPools.isReadOnlySelectSql("select score from risk_result for update"));
        assertFalse(DBConnectPools.isReadOnlySelectSql("select score from risk_result lock in share mode"));
    }

    @Test
    public void validationQueryRejectsWriteStatementsBeforeOpeningConnection() {
        assertEquals("select 1", DBConnectPools.validationSql(" select 1; "));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> DBConnectPools.validationSql("update risk_result set score = 1"));
    }

    @Test
    public void snapshotIsSafeWhenNoPoolHasBeenOpened() {
        DBConnectPools pools = new DBConnectPools();
        assertEquals(0, pools.snapshot().get("poolCount"));
        assertEquals(0L, pools.snapshot().get("pending"));
    }

    private RuleDbDatasource datasource(String dbType, String host, Integer port, String databaseName, String jdbcParams) {
        RuleDbDatasource datasource = new RuleDbDatasource();
        datasource.setDbType(dbType);
        datasource.setHost(host);
        datasource.setPort(port);
        datasource.setDatabaseName(databaseName);
        datasource.setJdbcParams(jdbcParams);
        return datasource;
    }
}
