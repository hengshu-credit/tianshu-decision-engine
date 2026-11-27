package com.hengshucredit.rule.server.schema;

import com.hengshucredit.rule.server.config.SchemaInitializationConfig;
import com.hengshucredit.rule.server.testdata.BuiltinMockRules;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.Assert.*;

/** Only creates and drops its own randomly named database on the local MySQL test service. */
public class MysqlSchemaUpgradeIntegrationTest {
    private Connection connection;
    private String database;
    private static final String TABLE = """
            CREATE TABLE IF NOT EXISTS `orders` (
              `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单标识',
              `code` VARCHAR(60) NOT NULL DEFAULT 'NEW' COMMENT '订单编码',
              `parent_id` BIGINT DEFAULT NULL COMMENT '上级标识',
              `amount` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '金额',
              `label` VARCHAR(80) GENERATED ALWAYS AS (concat('订单:', `code`)) STORED COMMENT '展示名称',
              PRIMARY KEY (`id`), KEY `idx_code` (`code`(8) DESC, `id`),
              CONSTRAINT `fk_parent` FOREIGN KEY (`parent_id`) REFERENCES `parent_table` (`id`) ON DELETE RESTRICT
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';
            CREATE TABLE IF NOT EXISTS `parent_table` (`id` BIGINT NOT NULL COMMENT '父标识', PRIMARY KEY (`id`))
            ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='父表';
            """;

    @Before public void openDatabase() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("schemaUpgrade.integration"));
        int port = Integer.parseInt(System.getenv().getOrDefault("SCHEMA_TEST_MYSQL_PORT", "3306"));
        String url = "jdbc:mysql://127.0.0.1:" + port + "/?allowPublicKeyRetrieval=true&useSSL=false&characterEncoding=UTF-8";
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("SCHEMA_TEST_MYSQL_USER", "root"),
                System.getenv().getOrDefault("SCHEMA_TEST_MYSQL_PASSWORD", ""));
        String name = "ts_schema_it_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE DATABASE " + name + " CHARACTER SET utf8mb4");
        database = name;
        connection.setCatalog(database);
    }
    @After public void closeDatabase() throws Exception {
        if (connection != null) try {
            if (database != null && database.matches("ts_schema_it_[a-f0-9]{32}")) execute("DROP DATABASE " + database);
        } finally { connection.close(); }
    }

    @Test public void upgradesLegacyDataChecksConstraintsAndRetriesPartialDdl() throws Exception {
        execute("CREATE TABLE orders(id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, code VARCHAR(12) NOT NULL,"
                + " parent_id BIGINT DEFAULT NULL, local_note TEXT, KEY idx_code(code)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        execute("INSERT INTO orders(code,parent_id,local_note) VALUES ('original',9,'保留客户扩展')");
        SQLException orphan = assertThrows(SQLException.class, () -> upgrade(TABLE));
        assertEquals("23000", orphan.getSQLState());
        assertEquals("original", scalar("SELECT code FROM orders WHERE id=1"));
        execute("INSERT INTO parent_table(id) VALUES(9)");
        assertEquals(1, upgrade(TABLE));
        assertEquals(0, upgrade(TABLE));
        assertEquals("订单:original", scalar("SELECT label FROM orders"));
        assertEquals("保留客户扩展", scalar("SELECT local_note FROM orders"));
        assertEquals("0.00", scalar("SELECT amount FROM orders"));
        assertEquals("8:D", scalar("SELECT CONCAT(sub_part,':',collation) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='orders' AND index_name='idx_code' AND seq_in_index=1"));
        execute("INSERT INTO orders(parent_id) VALUES(9)");
        assertEquals("NEW", scalar("SELECT code FROM orders WHERE id=2"));
        String narrowed = TABLE.replace("VARCHAR(60)", "VARCHAR(3)").replace("(`code`(8)", "(`code`(2)");
        assertTrue(assertThrows(IllegalStateException.class, () -> upgrade(narrowed)).getMessage().contains("orders.code"));
        assertTrue(assertThrows(IllegalStateException.class, () -> upgrade(TABLE.replace("`amount` DECIMAL", "`required` INT NOT NULL COMMENT '必填', `amount` DECIMAL"))).getMessage().contains("先回填"));
        assertTrue(assertThrows(IllegalStateException.class, () -> upgrade(TABLE.replace("concat('订单:',", "concat('新单:',"))).getMessage().contains("orders.label"));
        assertTrue(assertThrows(IllegalStateException.class, () -> upgrade(TABLE.replace("ON DELETE RESTRICT", "ON DELETE CASCADE"))).getMessage().contains("fk_parent"));
        assertEquals("original", scalar("SELECT code FROM orders WHERE id=1"));
    }

    @Test public void twoSpringInitializersWaitOnTheSamePhysicalDatabaseLock() throws Exception {
        var source = new DriverManagerDataSource(connection.getMetaData().getURL().replace("/?", "/" + database + "?"),
                System.getenv().getOrDefault("SCHEMA_TEST_MYSQL_USER", "root"), System.getenv().getOrDefault("SCHEMA_TEST_MYSQL_PASSWORD", ""));
        var properties = new SqlInitializationProperties();
        properties.setMode(DatabaseInitializationMode.ALWAYS);
        properties.setSchemaLocations(List.of("classpath:sql/schema.sql"));
        String lock = "tianshu:schema:" + UUID.nameUUIDFromBytes(database.getBytes(StandardCharsets.UTF_8));
        assertEquals("1", scalar("SELECT GET_LOCK('" + lock + "',0)"));
        var workers = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        try {
            java.util.concurrent.Callable<Boolean> boot = () -> {
                ready.countDown();
                return new SchemaInitializationConfig().dataSourceScriptDatabaseInitializer(source, properties).initializeDatabase();
            };
            var first = workers.submit(boot); var second = workers.submit(boot);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> first.get(250, TimeUnit.MILLISECONDS));
            assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
            assertEquals("0", scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"));
            assertEquals("1", scalar("SELECT RELEASE_LOCK('" + lock + "')"));
            assertTrue(first.get(90, TimeUnit.SECONDS));
            assertTrue(second.get(90, TimeUnit.SECONDS));
            assertEquals("1", scalar("SELECT COUNT(*) FROM rule_variable WHERE var_code='DECISION_PASS'"));
            assertEquals("4", scalar("SELECT COUNT(*) FROM rule_system_setting"));
            assertEquals("17", scalar("SELECT COUNT(*) FROM rule_mock_rule WHERE generator <> 'FAKER'"));
            execute("ALTER TABLE rule_project MODIFY description VARCHAR(1000) DEFAULT NULL COMMENT '旧版本较长说明'");
            properties.setContinueOnError(true);
            assertThrows(IllegalStateException.class, () -> new SchemaInitializationConfig()
                    .dataSourceScriptDatabaseInitializer(source, properties).initializeDatabase());
            assertEquals("varchar(1000)", scalar("SELECT column_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_project' AND column_name='description'"));
            // A different session can take over immediately after initialization.
            assertEquals("1", scalar("SELECT GET_LOCK('" + lock + "',0)"));
            assertEquals("1", scalar("SELECT RELEASE_LOCK('" + lock + "')"));
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test public void packagedSchemaRepairsDriftEvenAfterACompleteRun() throws Exception {
        String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        assertTrue(upgrade(schema) > 0);
        assertEquals(0, upgrade(schema));
        execute("ALTER TABLE rule_variable DROP COLUMN system_constant");
        execute("ALTER TABLE rule_project DROP INDEX uk_project_code");
        assertTrue(upgrade(schema) >= 2);
        assertEquals("1", scalar("SELECT COUNT(*) FROM rule_variable WHERE var_code='DECISION_PASS'"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM information_schema.views WHERE table_schema=DATABASE() AND table_name='rule_execution_log_current'"));
        assertEquals(0, upgrade(schema));
        execute("ALTER TABLE rule_definition_version COALESCE PARTITION 1");
        assertTrue(assertThrows(IllegalStateException.class, () -> upgrade(schema)).getMessage().contains("分区"));
    }

    @Test public void packagedMockSeedsUpgradeInPlaceAndPreserveFieldBindingsAndUserEdits() throws Exception {
        String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        upgrade(schema);
        BuiltinMockRules.install(connection);
        assertEquals("17", scalar("SELECT COUNT(*) FROM rule_mock_rule"));
        String id = scalar("SELECT id FROM rule_mock_rule WHERE seed_key='v2.mobile'");
        execute("UPDATE rule_mock_rule SET seed_key='mobile', name='人工手机', match_names='contactPhone', "
                + "generator='FIXED', config_json=JSON_QUOTE('example') WHERE id=" + id);
        execute("UPDATE rule_mock_rule SET seed_key='number', match_names='*' WHERE seed_key='v2.number'");
        execute("UPDATE rule_variable SET mock_rule_id=" + id + " WHERE var_code='trace_id' AND project_id=0");
        execute("DELETE FROM rule_mock_rule WHERE seed_key='decimal'");
        BuiltinMockRules.install(connection);
        BuiltinMockRules.install(connection);
        assertEquals("17", scalar("SELECT COUNT(*) FROM rule_mock_rule"));
        assertEquals("", scalar("SELECT match_names FROM rule_mock_rule WHERE seed_key='v2.number'"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM rule_mock_rule WHERE seed_key='decimal'"));
        assertEquals(id, scalar("SELECT r.id FROM rule_mock_rule r JOIN rule_variable v ON v.mock_rule_id=r.id "
                + "WHERE v.var_code='trace_id' AND r.seed_key='v2.mobile' AND r.name='人工手机' "
                + "AND r.match_names='contactPhone' AND r.generator='FIXED' AND JSON_UNQUOTE(r.config_json)='example'"));
    }

    @Test public void packagedStartupFillsOnlyMissingObjectScriptNamesAndKeepsUpdateTimes() throws Exception {
        var source = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
        var properties = new SqlInitializationProperties();
        properties.setMode(DatabaseInitializationMode.ALWAYS);
        properties.setSchemaLocations(List.of("classpath:sql/schema.sql"));
        upgrade(new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8));
        execute("""
                INSERT INTO rule_data_object(project_id, object_code, script_name, update_time)
                VALUES (0, 'Legacy_CodeA', NULL, '2026-01-02 03:04:05'),
                       (0, 'Legacy_CodeB', '   ', '2026-01-02 03:04:05'),
                       (0, 'Legacy_CodeC', 'myAlias', '2026-01-02 03:04:05')
                """);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertTrue(new SchemaInitializationConfig().dataSourceScriptDatabaseInitializer(source, properties).initializeDatabase());
            assertEquals("Legacy_CodeA,Legacy_CodeB,myAlias", scalar("SELECT GROUP_CONCAT(script_name ORDER BY object_code) "
                    + "FROM rule_data_object WHERE object_code IN ('Legacy_CodeA','Legacy_CodeB','Legacy_CodeC') "
                    + "AND update_time='2026-01-02 03:04:05'"));
        }
    }

    @Test public void customBootstrapRecreatesBothInvokerViewsWithoutInstallingPackagedSeeds() throws Exception {
        upgrade(new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8));
        execute("DROP VIEW rule_execution_statistics_current, rule_execution_log_current");
        var script = java.nio.file.Files.createTempFile("tianshu-custom-schema-", ".sql");
        try {
            java.nio.file.Files.writeString(script, "SELECT 1;");
            var properties = new SqlInitializationProperties();
            properties.setMode(DatabaseInitializationMode.ALWAYS);
            properties.setSchemaLocations(List.of(script.toUri().toString()));
            var source = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
            assertTrue(new SchemaInitializationConfig().dataSourceScriptDatabaseInitializer(source, properties).initializeDatabase());
            assertEquals("2", scalar("SELECT COUNT(*) FROM information_schema.views WHERE table_schema=DATABASE() "
                    + "AND table_name IN ('rule_execution_log_current','rule_execution_statistics_current') AND security_type='INVOKER'"));
            assertEquals("0", scalar("SELECT COUNT(*) FROM rule_mock_rule WHERE seed_key='decimal'"));
        } finally {
            java.nio.file.Files.deleteIfExists(script);
        }
    }

    @Test public void rejectsInvalidLegacyJsonAndConvertsValidConfiguration() throws Exception {
        String schema = "CREATE TABLE rule_external_datasource (id INT NOT NULL COMMENT '来源标识', "
                + "auth_config JSON DEFAULT NULL COMMENT '鉴权配置', PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外数来源'";
        execute("CREATE TABLE rule_external_datasource (id INT PRIMARY KEY, auth_config TEXT) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        execute("INSERT INTO rule_external_datasource VALUES (7,'unparsed')");
        assertTrue(assertThrows(IllegalStateException.class, () -> upgrade(schema)).getMessage().contains("非法 JSON"));
        assertEquals("unparsed", scalar("SELECT auth_config FROM rule_external_datasource"));
        execute("UPDATE rule_external_datasource SET auth_config=JSON_OBJECT('mode','legacy') WHERE id=7");
        assertEquals(1, upgrade(schema));
        assertEquals("legacy", scalar("SELECT auth_config->>'$.mode' FROM rule_external_datasource WHERE id=7"));
        assertEquals(0, upgrade(schema));
    }

    @Test public void packagedSchemaUpgradesLegacyAuditAndExecutionDataOnRestart() throws Exception {
        String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        upgrade(schema);
        execute("ALTER TABLE rule_billing_summary DROP INDEX uk_billing_summary_key, DROP COLUMN actor_user_id, "
                + "ADD UNIQUE KEY uk_billing_summary_key(summary_date,project_code,billing_code,billing_target,target_ref_id,auth_id,currency)");
        execute("ALTER TABLE console_operation_log DROP INDEX idx_console_operation_user_last, DROP COLUMN leave_time");
        execute("DROP TABLE console_operation_log_lock, rule_history_bucket_revision");
        execute("ALTER TABLE rule_execution_state DROP COLUMN dispatch_json");
        execute("ALTER TABLE rule_experiment_execution_state DROP COLUMN dispatch_json, DROP COLUMN request_json");
        execute("ALTER TABLE rule_experiment DROP COLUMN routing_error_policy");
        execute("ALTER TABLE rule_experiment_group ALTER COLUMN invoke_external_source SET DEFAULT 1");
        execute("ALTER TABLE rule_experiment_execution_log MODIFY input_params TEXT, MODIFY output_result TEXT");
        execute("ALTER TABLE rule_list_action_receipt DROP COLUMN action_source");
        execute("ALTER TABLE rule_list_record_log DROP COLUMN action_source");
        execute("DELETE FROM rule_variable WHERE var_code='DECISION_REVIEW'");

        execute("INSERT INTO rule_billing_summary(summary_date,billing_code,billing_target,total_count) VALUES ('2026-11-01','fee','ENGINE',9)");
        execute("INSERT INTO console_operation_log(username,module,action,request_method,request_path,outcome,result_code,duration_ms) "
                + "VALUES ('legacy','rule','VIEW','GET','/rule','SUCCESS',200,8)");
        execute("INSERT INTO rule_experiment(id,experiment_code,experiment_name) VALUES (17,'trial','旧实验')");
        String newGroup = "INSERT INTO rule_experiment_group(experiment_id,group_code,group_name,group_type,rule_code) ";
        execute(newGroup + "VALUES (17,'old','旧组','TEST','test_rule')");
        execute("INSERT INTO rule_experiment_execution_log(experiment_id,experiment_code,stage,input_params,output_result) "
                + "VALUES (17,'trial','TEST','legacy-input','legacy-output')");
        execute("INSERT INTO rule_list_action_receipt(task_id,changed_count) VALUES ('legacy-receipt',3)");

        assertTrue(upgrade(schema) > 0);
        assertEquals("1", scalar("SELECT COUNT(*) FROM rule_billing_summary WHERE total_count=9 AND actor_user_id IS NULL"));
        assertEquals("actor_user_id,summary_date,project_id,project_code,billing_code,billing_target,target_ref_id,auth_id,currency",
                scalar("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics "
                        + "WHERE table_schema=DATABASE() AND table_name='rule_billing_summary' AND index_name='uk_billing_summary_key' AND non_unique=0"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM console_operation_log WHERE username='legacy' AND leave_time IS NULL AND duration_ms=8"));
        assertEquals("FALLBACK", scalar("SELECT routing_error_policy FROM rule_experiment WHERE id=17"));
        execute(newGroup + "VALUES (17,'new','新组','TEST','test_rule')");
        assertEquals("1", scalar("SELECT invoke_external_source FROM rule_experiment_group WHERE group_code='old'"));
        assertEquals("0", scalar("SELECT invoke_external_source FROM rule_experiment_group WHERE group_code='new'"));
        assertEquals("legacy-input:legacy-output", scalar("SELECT CONCAT(input_params,':',output_result) FROM rule_experiment_execution_log WHERE experiment_id=17"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM rule_list_action_receipt WHERE task_id='legacy-receipt' AND action_source IS NULL AND changed_count=3"));
        assertEquals("2", scalar("SELECT default_value FROM rule_variable WHERE scope='GLOBAL' AND project_id=0 AND var_code='DECISION_REVIEW' AND system_constant=1"));
        assertEquals(0, upgrade(schema));
    }

    private int upgrade(String schema) throws SQLException { return new MysqlSchemaUpgrade(connection).run(schema); }
    private void execute(String sql) throws SQLException { try (var stmt = connection.createStatement()) { stmt.execute(sql); } }
    private String scalar(String sql) throws SQLException {
        try (var stmt = connection.createStatement(); var row = stmt.executeQuery(sql)) { assertTrue(row.next()); return row.getString(1); }
    }
}
