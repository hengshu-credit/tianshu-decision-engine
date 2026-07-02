package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DBConnectPoolsTest {

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
