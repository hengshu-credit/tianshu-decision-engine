package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/** Explicit opt-in, loopback MySQL and newly created databases only. Never imports into the configured business database. */
public class ExampleDataImportIntegrationTest {
    @Test(timeout = 300000)
    public void importsWithAndWithoutInitialDataAndWithShiftedIds() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("exampleData.integration"));
        for (String mode : List.of("OFF", "ON", "LEGACY", "SHIFTED")) {
            try (Database db = new Database()) {
                if (mode.equals("SHIFTED")) {
                    int next = 1000;
                    for (String table : List.of("rule_project", "rule_variable", "rule_data_object", "rule_data_object_field",
                            "rule_model", "rule_model_output_field", "rule_list_library", "rule_db_datasource",
                            "rule_external_datasource", "rule_external_api_config", "rule_definition")) {
                        db.jdbc.execute("ALTER TABLE " + table + " AUTO_INCREMENT=" + next);
                        next += 1000;
                    }
                }
                if (!mode.equals("OFF")) db.initial();
                if (mode.equals("LEGACY")) db.execute(new ClassPathResource("sql/legacy-initial-source-variables.sql"));
                db.example();
                audit(db.jdbc);
                String snapshot = db.snapshot();
                db.example();
                assertEquals(mode + " repeated import changed saved rows", snapshot, db.snapshot());
                db.initial();
                db.execute(new ClassPathResource("sql/schema.sql"));
                assertEquals(mode + " initialization after import changed saved rows", snapshot, db.snapshot());
                System.out.println("SEED_IMPORT " + mode + " passed: variables=208, objects=8, fields=36, model inputs=9, repeated content unchanged");
            }
        }
    }

    @Test(timeout = 120000)
    public void preservesCustomizedInitialConfiguration() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("exampleData.integration"));
        try (Database db = new Database()) {
            db.initial();
            db.execute(new ClassPathResource("sql/legacy-initial-source-variables.sql"));
            db.jdbc.update("UPDATE rule_variable SET var_label='保留已有配置',default_value='custom' WHERE var_code='credit_id'");
            db.jdbc.update("UPDATE rule_variable SET source_config=JSON_OBJECT('datasourceId',999,'sql','SELECT 99') WHERE var_code='black_user_count'");
            String custom = db.jdbc.queryForObject("SELECT source_config FROM rule_variable WHERE var_code='black_user_count'", String.class);
            db.example();
            assertEquals("保留已有配置", db.jdbc.queryForObject("SELECT var_label FROM rule_variable WHERE var_code='credit_id'", String.class));
            assertEquals("custom", db.jdbc.queryForObject("SELECT default_value FROM rule_variable WHERE var_code='credit_id'", String.class));
            assertEquals(custom, db.jdbc.queryForObject("SELECT source_config FROM rule_variable WHERE var_code='black_user_count'", String.class));
        }
    }

    @Test(timeout = 120000)
    public void ambiguousGlobalIdentityFailsAndRollsBackTheImport() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("exampleData.integration"));
        try (Database db = new Database()) {
            db.jdbc.update("INSERT INTO rule_variable(project_id,scope,var_code,var_label,var_type,var_source,status) VALUES (0,'GLOBAL','age','A','NUMBER','INPUT',1),(7,'GLOBAL','age','B','NUMBER','INPUT',1)");
            String before = db.snapshot();
            assertThrows(RuntimeException.class, db::example);
            assertEquals(before, db.snapshot());
        }
    }

    private static void audit(JdbcTemplate jdbc) {
        assertEquals(Integer.valueOf(208), jdbc.queryForObject("SELECT COUNT(*) FROM rule_variable", Integer.class));
        assertEquals(Integer.valueOf(8), jdbc.queryForObject("SELECT COUNT(*) FROM rule_data_object", Integer.class));
        assertEquals(Integer.valueOf(36), jdbc.queryForObject("SELECT COUNT(*) FROM rule_data_object_field", Integer.class));
        assertEquals(Integer.valueOf(9), jdbc.queryForObject("SELECT COUNT(*) FROM rule_model_input_field", Integer.class));
        for (String table : List.of("rule_definition_input_field", "rule_definition_output_field", "rule_model_output_field")) {
            assertEquals(table, Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " f LEFT JOIN rule_variable v ON v.id=f.var_id WHERE f.ref_type='VARIABLE' AND (v.id IS NULL OR f.field_name<>v.var_code)", Integer.class));
        }
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM rule_model_input_field f LEFT JOIN rule_data_object_field d ON d.id=f.var_id LEFT JOIN rule_data_object o ON o.id=d.object_id WHERE f.ref_type<>'DATA_OBJECT' OR d.id IS NULL OR d.var_code<>f.field_name OR o.object_code<>'score_f1_fields'", Integer.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT object_id,var_code,COUNT(*) n FROM rule_data_object_field GROUP BY object_id,parent_field_id,var_code HAVING n>1) duplicates", Integer.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM rule_variable_option x LEFT JOIN rule_variable v ON v.id=x.variable_id WHERE v.id IS NULL OR v.var_type<>'ENUM'", Integer.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM rule_data_object_field_option x LEFT JOIN rule_data_object_field f ON f.id=x.field_id WHERE f.id IS NULL OR f.var_code NOT IN ('relation','relation_type')", Integer.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM rule_experiment_group g LEFT JOIN rule_definition d ON d.id=g.rule_id WHERE d.id IS NULL OR d.rule_code<>g.rule_code", Integer.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM rule_definition d LEFT JOIN rule_project p ON p.id=d.project_id WHERE d.scope='PROJECT' AND (p.id IS NULL OR d.project_code<>p.project_code)", Integer.class));
        Long mobile = jdbc.queryForObject("SELECT id FROM rule_variable WHERE var_code='mobile_no'", Long.class);
        Long list = jdbc.queryForObject("SELECT id FROM rule_list_library WHERE list_code='black_list'", Long.class);
        Long database = jdbc.queryForObject("SELECT id FROM rule_db_datasource WHERE datasource_code='rule_engine_mysql'", Long.class);
        Long api = jdbc.queryForObject("SELECT id FROM rule_external_api_config WHERE api_code='BDZR'", Long.class);
        JSONObject db = JSON.parseObject(jdbc.queryForObject("SELECT source_config FROM rule_variable WHERE var_code='black_user_count'", String.class));
        assertEquals(mobile, db.getJSONArray("params").getJSONObject(0).getLong("refId"));
        assertEquals(database, db.getLong("datasourceId"));
        assertTrue(db.getString("sql"), db.getString("sql").contains("list_id = " + list + " and item_content = ?"));
        JSONObject listConfig = JSON.parseObject(jdbc.queryForObject("SELECT source_config FROM rule_variable WHERE var_code='black_hit'", String.class));
        assertEquals(list, listConfig.getJSONArray("listIds").getLong(0));
        JSONObject listOperand = listConfig.getJSONArray("queryOperands").getJSONObject(0);
        assertEquals("REFERENCE", listOperand.getString("kind"));
        assertEquals("VARIABLE", listOperand.getString("refType"));
        assertEquals(mobile, listOperand.getLong("refId"));
        assertEquals("ANY_FIELD_ANY_LIST", listConfig.getString("combinationMode"));
        assertFalse(listConfig.containsKey("listId"));
        assertFalse(listConfig.containsKey("queryField"));
        assertEquals(api, JSON.parseObject(jdbc.queryForObject("SELECT source_config FROM rule_variable WHERE var_code='engine_bdrules'", String.class)).getLong("apiConfigId"));
        Map<Long, String> variables = catalog(jdbc, "SELECT id,var_code code FROM rule_variable");
        Map<Long, String> outputs = catalog(jdbc, "SELECT f.id,CONCAT(m.model_code,'.',f.field_name) code FROM rule_model_output_field f JOIN rule_model m ON m.id=f.model_id");
        for (String query : List.of("SELECT model_json FROM rule_definition_content", "SELECT model_json FROM rule_definition_version",
                "SELECT model_json FROM rule_published", "SELECT execution_config FROM rule_external_api_config", "SELECT source_config FROM rule_variable WHERE source_config IS NOT NULL")) {
            for (String text : jdbc.queryForList(query, String.class)) checkJson(JSON.parse(text), variables, outputs);
        }
    }

    private static Map<Long, String> catalog(JdbcTemplate jdbc, String query) {
        Map<Long, String> result = new HashMap<>();
        jdbc.query(query, rs -> { result.put(rs.getLong("id"), rs.getString("code")); });
        return result;
    }

    private static void checkJson(Object value, Map<Long, String> variables, Map<Long, String> outputs) {
        if (value instanceof JSONObject object) {
            for (String key : List.of("_varId", "_targetVarId", "refId")) {
                Long id = object.getLong(key);
                if (id == null) continue;
                String type = object.getString(key.equals("refId") ? "refType" : key.equals("_targetVarId") ? "_targetRefType" : "_refType");
                String code = key.equals("refId") ? object.getString("code") : key.equals("_targetVarId") ? object.getString("target") : object.getString("varCode");
                Map<Long, String> target = "MODEL_OUTPUT".equals(type) ? outputs : "VARIABLE".equals(type) || "CONSTANT".equals(type) ? variables : null;
                if (target != null) { assertTrue(type + ":" + id, target.containsKey(id)); if (code != null) assertEquals(code, target.get(id)); }
            }
            object.values().forEach(child -> checkJson(child, variables, outputs));
        } else if (value instanceof List<?> array) array.forEach(child -> checkJson(child, variables, outputs));
    }

    private static final class Database implements AutoCloseable {
        private final DriverManagerDataSource admin;
        private final DriverManagerDataSource source;
        private final String name = "codex_seed_" + UUID.randomUUID().toString().replace("-", "");
        private final JdbcTemplate jdbc;
        private final Path root;
        private Database() throws Exception {
            root = Files.isDirectory(Path.of("rule-engine-server")) ? Path.of("").toAbsolutePath() : Path.of("").toAbsolutePath().getParent();
            admin = (DriverManagerDataSource) BillingSummaryCurrencyIntegrationTest.localDataSource();
            String url = Objects.requireNonNull(admin.getUrl()).replace("socketTimeout=15000", "socketTimeout=180000");
            int query = url.indexOf('?'); int slash = url.lastIndexOf('/', query);
            source = new DriverManagerDataSource(url.substring(0, slash + 1) + name + url.substring(query), admin.getUsername(), admin.getPassword());
            new JdbcTemplate(admin).execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            jdbc = new JdbcTemplate(source);
            try { execute(new ClassPathResource("sql/schema.sql")); }
            catch (Exception | Error error) { close(); throw error; }
        }
        private void initial() { execute(new ClassPathResource("sql/initial-data.sql")); }
        private void example() { execute(new FileSystemResource(root.resolve("docker/rule-engine-mysql/data-tianshu-example.sql"))); }
        private void execute(Resource script) {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(script);
            populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
            try (Connection connection = source.getConnection()) { populator.populate(connection); }
            catch (Exception error) { throw new IllegalStateException("Seed integration script failed", error); }
        }
        private String snapshot() throws Exception {
            String sql = Files.readString(root.resolve("docker/rule-engine-mysql/data-tianshu-example.sql"));
            var matcher = Pattern.compile("(?im)^INSERT INTO (\\w+)").matcher(sql);
            Map<String, Object> content = new TreeMap<>();
            while (matcher.find()) {
                String table = matcher.group(1);
                if (!content.containsKey(table)) content.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
            }
            return JSON.toJSONString(content);
        }
        @Override public void close() {
            if (!name.matches("codex_seed_[a-f0-9]{32}")) throw new IllegalStateException("Invalid test database cleanup target");
            new JdbcTemplate(admin).execute("DROP DATABASE `" + name + "`");
        }
    }
}
