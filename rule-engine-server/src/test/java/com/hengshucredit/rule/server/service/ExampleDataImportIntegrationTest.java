package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.RuleEngineApplication;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.example.CreditExampleImport;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.Assert.*;

/** Opt-in: local MySQL + Redis. Creates and cleans only randomly named test databases. */
public class ExampleDataImportIntegrationTest {
    @Test(timeout = 900000)
    public void importsWithAndWithoutInitialDataAndWithShiftedIds() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("exampleData.integration"));
        for (String mode : List.of("OFF", "ON", "LEGACY", "SHIFTED")) {
            try (Database db = new Database()) {
                if (!mode.equals("OFF")) db.initial();
                if (mode.equals("LEGACY")) db.runSql("sql/legacy-initial-source-variables.sql");
                if (mode.equals("SHIFTED")) {
                    int next = 1000;
                    for (String table : List.of("rule_project", "rule_variable", "rule_data_object_field", "rule_model",
                            "rule_model_output_field", "rule_model_version", "rule_definition", "rule_external_api_config")) {
                        db.jdbc.execute("ALTER TABLE " + table + " AUTO_INCREMENT=" + next);
                        next += 1000;
                    }
                }
                if (!mode.equals("OFF")) db.jdbc.update("UPDATE rule_variable SET var_label='已有用户标签' WHERE scope='GLOBAL' AND var_code='mobile_no'");
                db.load();
                assertEquals(2, db.count("rule_project"));
                assertEquals(20, db.count("rule_published"));
                assertEquals(2, (int) db.jdbc.queryForObject("SELECT COUNT(*) FROM rule_published WHERE rule_code IN ('NBQZ','CREDIT_ADMISSION') AND version=3 AND status=1", Integer.class));
                assertEquals(1, (int) db.jdbc.queryForObject("""
                        SELECT COUNT(*) FROM rule_published p JOIN rule_definition d ON p.definition_id=d.id
                        JOIN rule_variable v ON v.id=CAST(JSON_UNQUOTE(JSON_EXTRACT(p.model_json,'$.rules[0].conditionRoot.children[0].leftOperand.refId')) AS UNSIGNED)
                        WHERE p.rule_code='NBQZ' AND v.var_code='current_overdue_days' AND v.var_source='DB' AND v.project_id=d.project_id
                        """, Integer.class));
                assertEquals(20, db.count("decision_artifact"));
                assertEquals(8, db.count("rule_external_api_config"));
                assertEquals(8, db.count("rule_billing_config"));
                assertEquals(7, db.count("rule_model_input_field"));
                assertEquals(1, db.count("rule_model_output_field"));
                assertEquals(115, db.count("rule_api_doc_scenario"));
                assertEquals(46, db.count("rule_api_test_case"));
                assertEquals(95, db.count("rule_variable_test_case"));
                assertEquals(2, db.count("rule_model_test_case"));
                assertEquals(20, (int) db.jdbc.queryForObject("SELECT COUNT(DISTINCT definition_id) FROM rule_api_doc_scenario WHERE status=1", Integer.class));
                assertEquals(24, (int) db.jdbc.queryForObject("SELECT COUNT(DISTINCT variable_id) FROM rule_variable_test_case", Integer.class));
                assertEquals(8, (int) db.jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT api_id FROM rule_api_test_case WHERE case_code IN ('PASS','REJECT','EDGE','NULL','NO-HIT') GROUP BY api_id HAVING COUNT(*)=5) covered", Integer.class));
                assertEquals(0, (int) db.jdbc.queryForObject("SELECT COUNT(*) FROM rule_api_doc_scenario s LEFT JOIN rule_published p ON p.definition_id=s.definition_id AND p.revision_id=s.revision_id AND p.artifact_digest=s.artifact_digest WHERE p.id IS NULL", Integer.class));
                assertEquals(1, db.count("rule_list_record"));
                assertTrue(db.count("governance_dependency_snapshot") > 0);
                for (String table : List.of("rule_billing_record", "rule_runtime_call_log", "rule_execution_log",
                        "rule_list_record_log", "rule_execution_checkpoint", "rule_application_history"))
                    assertEquals(table, 0, db.count(table));
                assertEquals(7, (int) db.jdbc.queryForObject("""
                        SELECT COUNT(*) FROM rule_model_input_field f JOIN rule_variable v ON v.id=f.var_id
                        WHERE f.ref_type='VARIABLE' AND v.var_code=f.field_name AND v.var_source='API'
                        """, Integer.class));
                assertEquals(8, (int) db.jdbc.queryForObject("""
                        SELECT COUNT(*) FROM rule_billing_config b JOIN rule_external_api_config a ON a.id=b.target_ref_id
                        WHERE b.billing_code=CONCAT('API_',a.id) AND a.unit_price=b.unit_price
                        """, Integer.class));
                if (!mode.equals("OFF")) assertEquals("已有用户标签",db.jdbc.queryForObject(
                        "SELECT var_label FROM rule_variable WHERE scope='GLOBAL' AND var_code='mobile_no'",String.class));
                var snapshot = db.snapshot();
                db.load();
                assertEquals(mode, snapshot, db.snapshot());
                if (!mode.equals("OFF")) {
                    db.initial();
                    assertEquals("initial-data after example changed configuration", snapshot, db.snapshot());
                }
            }
        }
    }

    @Test(timeout = 240000)
    public void ambiguousGlobalIdentityFailsAndRollsBackProjects() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("exampleData.integration"));
        try (Database db = new Database()) {
            db.jdbc.update("INSERT INTO rule_project(project_code,project_name) VALUES ('EXISTING','既有项目')");
            Long project = db.jdbc.queryForObject("SELECT id FROM rule_project WHERE project_code='EXISTING'", Long.class);
            db.jdbc.update("INSERT INTO rule_variable(project_id,scope,var_code,var_label,var_type,var_source,status) VALUES (0,'GLOBAL','mobile_no','A','NUMBER','INPUT',1),(?,'GLOBAL','mobile_no','B','NUMBER','INPUT',1)", project);
            var before = db.snapshot();
            assertThrows(Exception.class, db::load);
            assertEquals(before, db.snapshot());
        }
    }

    private static final class Database implements AutoCloseable {
        private final String name = "codex_seed_" + UUID.randomUUID().toString().replace("-", "");
        private final DriverManagerDataSource admin;
        private final ConfigurableApplicationContext context;
        private final JdbcTemplate jdbc;
        private final Path credentials;
        private final Path sql;

        private Database() throws Exception {
            var root = Files.isDirectory(Path.of("docker")) ? Path.of("") : Path.of("..");
            sql = root.resolve("docker/rule-engine-mysql/data-tianshu-example.sql");
            admin = (DriverManagerDataSource) BillingSummaryCurrencyIntegrationTest.localDataSource();
            String url = Objects.requireNonNull(admin.getUrl()).replace("socketTimeout=15000", "socketTimeout=180000");
            int query = url.indexOf('?');
            String target = url.substring(0, url.lastIndexOf('/', query) + 1) + name + url.substring(query);
            new JdbcTemplate(admin).execute("CREATE DATABASE " + name + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            credentials = Files.createTempFile("credit-example-test-", ".json");
            var keys = new HashMap<String,Object>();
            for (String key : List.of("court","black","late","multi","income","fraud","credit","pboc","sftp","db_user","db_password","credit_api","engine"))
                keys.put(key, UUID.randomUUID().toString());
            keys.put("host_public", "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKiKQWBnJ6dSKwE9Zu2Y89a8vrA7H2vS/xuVHFOThEJw");
            Files.writeString(credentials, CanonicalJson.write(keys));
            try {
                // Use the production controllers in this full-context test. Other suites declare
                // nested mock controllers with deliberately identical request mappings.
                context = new SpringApplicationBuilder(RuleEngineApplication.class).initializers(ctx ->
                        ctx.getBeanFactory().registerSingleton("testClasspathFilter", new org.springframework.boot.context.TypeExcludeFilter() {
                            @Override public boolean match(org.springframework.core.type.classreading.MetadataReader reader,
                                    org.springframework.core.type.classreading.MetadataReaderFactory factory) throws java.io.IOException {
                                return reader.getResource().getURL().toExternalForm().replace('\\','/').contains("/target/test-classes/");
                            }
                        })).run("--server.port=0", "--server.address=127.0.0.1",
                        "--spring.datasource.url=" + target, "--spring.datasource.username=" + admin.getUsername(),
                        "--spring.datasource.password=" + admin.getPassword(), "--rule-engine.initial-data.enabled=false");
            } catch (RuntimeException error) {
                cleanup();
                throw error;
            }
            jdbc = context.getBean(JdbcTemplate.class);
        }
        private void load() throws Exception { new CreditExampleImport(context, credentials).load(sql); }
        private void runSql(String path) { new ResourceDatabasePopulator(new ClassPathResource(path)).execute(Objects.requireNonNull(jdbc.getDataSource())); }
        private void initial() { runSql("sql/initial-data.sql"); }
        private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
        private List<Map<String,Object>> snapshot() {
            return jdbc.queryForList("CHECKSUM TABLE rule_project,rule_project_auth,rule_variable,rule_variable_option,rule_data_object,rule_data_object_field,rule_model,rule_model_version,rule_definition,rule_definition_version,rule_version_binding,rule_published,decision_artifact,decision_artifact_component,governed_resource,governed_resource_version,governance_approval_request,governance_dependency_snapshot,rule_list_library,rule_list_record,rule_db_datasource,rule_sftp_datasource,rule_external_datasource,rule_external_api_config,rule_billing_config,rule_variable_test_case,rule_api_test_case,rule_model_test_case,rule_api_doc_scenario");
        }
        private void cleanup() throws Exception {
            Files.deleteIfExists(credentials);
            if (!name.matches("codex_seed_[a-f0-9]{32}")) throw new IllegalStateException("Invalid isolated database name");
            new JdbcTemplate(admin).execute("DROP DATABASE " + name);
        }
        @Override public void close() throws Exception { try { context.close(); } finally { cleanup(); } }
    }
}
