package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.junit.Assume;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/** 独有本机数据库中的合成容量数据，不是 UI 功能验收或业务初始化。 */
public class RuleLineageScaleIntegrationTest {
    @Test(timeout = 180000)
    public void measuresOneTenAndFiftyThousandReferencesWithRealMappers() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("lineage.integration"));
        var admin = (DriverManagerDataSource) BillingSummaryCurrencyIntegrationTest.localDataSource();
        String database = "codex_lineage_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate adminJdbc = new JdbcTemplate(admin);
        adminJdbc.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
        HikariConfig pool = new HikariConfig();
        String url = admin.getUrl(); int query = url.indexOf('?'); int slash = url.lastIndexOf('/', query);
        pool.setJdbcUrl(url.substring(0, slash + 1) + database + url.substring(query) + "&rewriteBatchedStatements=true");
        pool.setUsername(admin.getUsername()); pool.setPassword(admin.getPassword()); pool.setMaximumPoolSize(4);
        List<Map<String, Object>> measurements = new ArrayList<>();
        try (HikariDataSource source = new HikariDataSource(pool)) {
            JdbcTemplate jdbc = new JdbcTemplate(source);
            assertEquals(database, jdbc.queryForObject("SELECT DATABASE()", String.class));
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true); configuration.setLogImpl(NoLoggingImpl.class);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source); factory.setConfiguration(configuration); factory.setGlobalConfig(new GlobalConfig().setBanner(false));
            var sf = factory.getObject(); assertNotNull(sf);
            var sessions = new SqlSessionTemplate(sf);
            RuleLineageService service = new RuleLineageService();
            String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
            for (var field : RuleLineageService.class.getDeclaredFields()) {
                if (!BaseMapper.class.isAssignableFrom(field.getType())) continue;
                configuration.addMapper(field.getType());
                Class<?> entity = ResolvableType.forClass(field.getType()).as(BaseMapper.class).getGeneric(0).resolve();
                String table = TableInfoHelper.getTableInfo(entity).getTableName();
                int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
                assertTrue("缺少表结构: " + table, start >= 0);
                jdbc.execute(schema.substring(start, schema.indexOf(';', start) + 1));
                ReflectionTestUtils.setField(service, field.getName(), sessions.getMapper(field.getType()));
            }
            jdbc.update("INSERT INTO rule_project(id,project_code,project_name) VALUES (1,'scale','scale')");
            int previous = 0;
            for (int count : new int[]{1000, 10000, 50000}) {
                List<Integer> additions = java.util.stream.IntStream.rangeClosed(previous + 1, count).boxed().toList();
                jdbc.batchUpdate("INSERT INTO rule_variable(id,project_id,var_code,var_label,var_type) VALUES (?,1,?,?,'NUMBER')", additions, 1000,
                        (ps, id) -> { ps.setInt(1,id); ps.setString(2,"V" + id); ps.setString(3,"V" + id); });
                jdbc.batchUpdate("INSERT INTO rule_definition(id,project_id,rule_code,rule_name,model_type) VALUES (?,1,?,?,'SCRIPT')", additions, 1000,
                        (ps, id) -> { ps.setInt(1,id); ps.setString(2,"R" + id); ps.setString(3,"R" + id); });
                jdbc.batchUpdate("INSERT INTO rule_definition_input_field(definition_id,var_id,ref_type,field_name) VALUES (?,?,'VARIABLE',?)", additions, 1000,
                        (ps, id) -> { ps.setInt(1,id); ps.setInt(2,id); ps.setString(3,"V" + id); });
                List<Long> durations = new ArrayList<>();
                for (int sample = 0; sample < 12; sample++) {
                    long started = System.nanoTime();
                    Map<String,Object> result = service.graph("VARIABLE", 1L, "DOWNSTREAM", 1);
                    long elapsed = (System.nanoTime() - started) / 1_000_000;
                    List<?> nodes = (List<?>) result.get("nodes");
                    assertEquals(new LinkedHashSet<>(List.of("VARIABLE:1", "RULE:1")), nodes.stream()
                            .map(node -> ((Map<?,?>) node).get("id")).collect(java.util.stream.Collectors.toSet()));
                    assertEquals(1, ((List<?>) result.get("edges")).size());
                    if (sample >= 2) durations.add(elapsed);
                }
                durations.sort(Long::compare);
                long batchStarted = System.nanoTime();
                var consumers = service.directReferenceConsumers("VARIABLE", List.of(1L, (long) count));
                long batchMs = (System.nanoTime() - batchStarted) / 1_000_000;
                assertEquals("RULE:" + count, consumers.get((long) count).get(0).get("id"));
                assertEquals(1, consumers.get(1L).size());
                measurements.add(Map.of("variables", count, "rules", count, "references", count,
                        "samples", durations.size(), "medianMs", durations.get(5), "p95Ms", durations.get(9), "batchConsumersMs", batchMs));
                assertTrue("血缘 P95 超过本机 5 秒门限: " + measurements, durations.get(9) < 5000);
                previous = count;
            }
            // 规模夹具仍在独有测试库内；验证来源目录的真实 SQL 范围及超过 5000 项的完整性。
            List<Integer> sources = java.util.stream.IntStream.rangeClosed(1, 5001).boxed().toList();
            jdbc.batchUpdate("INSERT INTO rule_db_datasource(id,project_id,scope,datasource_code,datasource_name,jdbc_url,password) VALUES (?,1,'PROJECT',?,?,'jdbc:mysql://test.invalid/example','fixture-only')", sources, 1000,
                    (ps,id) -> { ps.setInt(1,id); ps.setString(2,"DB" + id); ps.setString(3,"DB" + id); });
            jdbc.update("INSERT INTO rule_db_datasource(id,project_id,scope,datasource_code,datasource_name,jdbc_url) VALUES (6001,0,'GLOBAL','GLOBAL','GLOBAL','jdbc:mysql://test.invalid/example'),(6002,2,'PROJECT','FOREIGN','FOREIGN','jdbc:mysql://test.invalid/example')");
            var catalog = new com.hengshucredit.rule.server.governance.VariableSourceReferenceValidator();
            for (var field : catalog.getClass().getDeclaredFields()) {
                if (BaseMapper.class.isAssignableFrom(field.getType())) ReflectionTestUtils.setField(catalog, field.getName(), sessions.getMapper(field.getType()));
            }
            long catalogStarted = System.nanoTime();
            var options = catalog.catalog("PROJECT", 1L).databaseOptions();
            assertEquals(5002, options.size());
            assertTrue(options.stream().anyMatch(option -> option.id().equals(5001L)));
            assertFalse(options.stream().anyMatch(option -> option.id().equals(6002L)));
            assertEquals(List.of(6001L), catalog.catalog("GLOBAL", 0L).databaseOptions().stream().map(com.hengshucredit.rule.server.governance.VariableSourceOption::id).toList());
            measurements.add(Map.of("catalogRows", options.size(), "catalogScopeValidated", true, "catalogMs", (System.nanoTime() - catalogStarted) / 1_000_000));
        } finally {
            if (!database.matches("codex_lineage_[a-f0-9]{32}")) throw new IllegalStateException("Invalid cleanup target");
            adminJdbc.execute("DROP DATABASE `" + database + "`");
        }
        String report = com.alibaba.fastjson.JSON.toJSONString(measurements, true);
        System.out.println("LINEAGE_SCALE " + report);
        if (System.getProperty("lineage.report") != null) Files.writeString(Path.of(System.getProperty("lineage.report")), report);
    }
}
