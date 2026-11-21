package com.hengshucredit.rule.server.testdata;

import com.hengshucredit.rule.server.security.ConsoleDataPermissionHandler;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.Assert.*;

/** MySQL JSON_TABLE、版本样例原文与项目过滤使用同一份查询。 */
public class SavedTestDirectoryIntegrationTest {
    @Test public void listsOriginalSamplesAndKeepsInvisibleResourcesOutOfDetailsAndCounts() {
        Assume.assumeTrue(Boolean.getBoolean("testDirectory.integration"));
        String url = System.getenv("TEST_DIRECTORY_MYSQL_URL");
        String dbName = "ts_case_it_"+UUID.randomUUID().toString().replace("-","");
        var ds = new DriverManagerDataSource(url,System.getenv("TEST_DIRECTORY_MYSQL_USER"),System.getenv("TEST_DIRECTORY_MYSQL_PASSWORD"));
        var admin = new JdbcTemplate(ds);
        admin.execute("CREATE DATABASE "+dbName+" CHARACTER SET utf8mb4");
        try {
            var db = new JdbcTemplate(new DriverManagerDataSource(url+dbName,ds.getUsername(),ds.getPassword()));
            db.execute("CREATE TABLE rule_external_api_config(id BIGINT,api_name VARCHAR(128),api_code VARCHAR(64),status INT,execution_config JSON,test_sample_params JSON)");
            db.execute("CREATE TABLE rule_api_test_case(id BIGINT,api_id BIGINT,case_name VARCHAR(128))");
            db.update("INSERT INTO rule_external_api_config VALUES (1,'评分','Score',1,?,?), (8,'不可见','Hidden',1,NULL,'{}')",
                    "{\"samples\":[{\"id\":\"s1\",\"name\":\"边界金额\",\"params\":{\"amount\":9007199254740993}}]}","{\"age\":18}");
            db.update("INSERT INTO rule_api_test_case VALUES(12,1,'历史固定用例')");
            db.execute("CREATE TABLE rule_model(id BIGINT,model_name VARCHAR(128),model_code VARCHAR(64),status INT,model_config JSON)");
            db.execute("CREATE TABLE rule_model_version(id BIGINT,model_id BIGINT,version INT,model_config JSON)");
            db.execute("CREATE TABLE rule_model_test_case(id BIGINT,model_id BIGINT,case_name VARCHAR(128))");
            db.update("INSERT INTO rule_model VALUES(1,'模型','Model',1,?)","{\"testParams\":\"{\\\"x\\\":0}\"}");
            db.update("INSERT INTO rule_model_version VALUES(9,1,3,?)","{\"testParams\":{\"x\":3}}");
            var projects = new ConsoleProjectAccess();
            var permissions = new ConsoleDataPermissionHandler(projects) {
                @Override public String predicate(String table,String alias) { return alias+".id<>8"; }
            };
            var directory = new SavedTestDirectory(db,permissions,projects);
            assertEquals(3L,directory.list(TestResource.API,"",1).get("total"));
            assertEquals(1L,directory.list(TestResource.API,"边界",1).get("total"));
            assertEquals(2L,directory.list(TestResource.MODEL,"",1).get("total"));
            assertEquals(9007199254740993L,MockRule.parse(directory.sample(TestResource.API,"API_NAMED",1,"s1")).get("amount").longValue());
            assertEquals(0,MockRule.parse(directory.sample(TestResource.MODEL,"MODEL_SAMPLE",1,"1")).get("x").intValue());
            assertEquals(3,MockRule.parse(directory.sample(TestResource.MODEL,"MODEL_VERSION",1,"3")).get("x").intValue());
            assertThrows(IllegalArgumentException.class,()->directory.sample(TestResource.API,"API_SAMPLE",8,"8"));
        } finally { admin.execute("DROP DATABASE "+dbName); }
    }
}
