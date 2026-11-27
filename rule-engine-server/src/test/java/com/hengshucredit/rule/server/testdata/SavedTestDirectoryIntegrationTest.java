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
            db.execute("CREATE TABLE rule_project(id BIGINT,project_name VARCHAR(64),status INT DEFAULT 1)");
            db.execute("INSERT INTO rule_project(id,project_name) VALUES (22,'测试项目'),(23,'其他项目')");
            db.execute("CREATE TABLE rule_external_datasource(id BIGINT,scope VARCHAR(8),project_id BIGINT,status INT)");
            db.execute("INSERT INTO rule_external_datasource VALUES (1,'PROJECT',22,1)");
            db.execute("CREATE TABLE rule_external_api_config(id BIGINT,api_name VARCHAR(128),api_code VARCHAR(64),status INT,datasource_id BIGINT,execution_config JSON,test_sample_params JSON)");
            db.execute("CREATE TABLE rule_api_test_case(id BIGINT,api_id BIGINT,case_name VARCHAR(128))");
            db.update("INSERT INTO rule_external_api_config VALUES (1,'评分','Score',1,1,?,?), (8,'不可见','Hidden',1,1,NULL,'{}')",
                    "{\"samples\":[{\"id\":\"s1\",\"name\":\"边界金额\",\"params\":{\"amount\":9007199254740993}}]}","{\"age\":18}");
            db.update("INSERT INTO rule_api_test_case VALUES(12,1,'历史固定用例')");
            db.execute("CREATE TABLE rule_model(id BIGINT,model_name VARCHAR(128),model_code VARCHAR(64),status INT,scope VARCHAR(8),project_id BIGINT,model_config JSON)");
            db.execute("CREATE TABLE rule_model_version(id BIGINT,model_id BIGINT,version INT,model_config JSON)");
            db.execute("CREATE TABLE rule_model_test_case(id BIGINT,model_id BIGINT,case_name VARCHAR(128))");
            db.update("INSERT INTO rule_model VALUES(1,'模型','Model',1,'GLOBAL',0,?)","{\"testParams\":\"{\\\"x\\\":0}\"}");
            db.update("INSERT INTO rule_model_version VALUES(9,1,3,?)","{\"testParams\":{\"x\":3}}");
            var projects = new ConsoleProjectAccess();
            var permissions = new ConsoleDataPermissionHandler(projects) {
                @Override public String predicate(String table,String alias) { return alias+".id<>8"; }
            };
            var directory = new SavedTestDirectory(db,permissions,projects);
            assertEquals(3L,directory.list(TestResource.API,"",1,null,null).get("total"));
            assertEquals(1L,directory.list(TestResource.API,"边界",1,null,null).get("total"));
            db.update("UPDATE rule_external_api_config SET test_sample_params=NULL WHERE id=1");
            assertEquals(3L,directory.list(TestResource.API,"",1,"PROJECT",22L).get("total"));
            assertEquals(1L,directory.list(TestResource.API,"默认测试输入",1,"PROJECT",22L).get("total"));
            assertEquals(0L,directory.list(TestResource.API,"",1,"GLOBAL",null).get("total"));
            assertEquals(2L,directory.list(TestResource.MODEL,"",1,null,null).get("total"));
            assertEquals(9007199254740993L,MockRule.parse(directory.sample(TestResource.API,"API_NAMED",1,"s1")).get("amount").longValue());
            assertEquals(0,MockRule.parse(directory.sample(TestResource.MODEL,"MODEL_SAMPLE",1,"1")).get("x").intValue());
            assertEquals(3,MockRule.parse(directory.sample(TestResource.MODEL,"MODEL_VERSION",1,"3")).get("x").intValue());
            assertThrows(IllegalArgumentException.class,()->directory.sample(TestResource.API,"API_SAMPLE",8,"8"));

            db.execute("CREATE TABLE governance_approval_request(id BIGINT,active_resource_key VARCHAR(96),status VARCHAR(16),actor_user_id BIGINT,draft_snapshot_json JSON)");
            db.update("INSERT INTO governance_approval_request VALUES (1,'EXTERNAL_API:1','EDITING',7,JSON_OBJECT('executionConfig',?))",
                    "{\"samples\":[{\"id\":\"draft\",\"name\":\"草稿样例\",\"params\":{\"count\":42}}]}");
            var actor = new ConsoleProjectAccess() {
                @Override public Scope current() { return new Scope(7L,java.util.Set.of(22L),java.util.Set.of("datasource:edit")); }
            };
            var workspace = new SavedTestDirectory(db,permissions,actor);
            assertEquals(1L,workspace.list(TestResource.API,"草稿样例",1,"PROJECT",22L).get("total"));
            assertEquals(42,MockRule.parse(workspace.sample(TestResource.API,"API_NAMED",1,"draft")).get("count").intValue());
            db.update("UPDATE governance_approval_request SET actor_user_id=10");
            assertEquals(0L,workspace.list(TestResource.API,"草稿样例",1,"PROJECT",22L).get("total"));
            db.execute("CREATE TABLE rule_function(id BIGINT,func_name VARCHAR(128),func_code VARCHAR(64),status INT,scope VARCHAR(8),project_id BIGINT,params_json JSON)");
            db.update("INSERT INTO rule_function VALUES(2,'计算金额','Amount',1,'PROJECT',22,?),"
                    +"(3,'尚无样例','Empty',1,'PROJECT',22,'[]'),(8,'不可见','Hidden',1,'PROJECT',22,?)",
                    "[{\"name\":\"amount\",\"example\":0}]", "[{\"name\":\"amount\",\"example\":99}]");
            assertEquals(1L,directory.list(TestResource.FUNCTION,"",1,"PROJECT",22L).get("total"));
            var functionEditor = new ConsoleProjectAccess() {
                @Override public Scope current() { return new Scope(7L,java.util.Set.of(22L),java.util.Set.of("function:edit")); }
            };
            var functionDirectory = new SavedTestDirectory(db,permissions,functionEditor);
            db.update("INSERT INTO governance_approval_request VALUES (2,'FUNCTION:3','EDITING',7,JSON_OBJECT('paramsJson',?))",
                    "[{\"name\":\"x\",\"example\":null}]");
            assertEquals(2L,functionDirectory.list(TestResource.FUNCTION,"",1,null,null).get("total"));
            assertEquals(0L,directory.list(TestResource.FUNCTION,"Empty",1,null,null).get("total"));
            db.update("UPDATE governance_approval_request SET draft_snapshot_json=JSON_OBJECT('paramsJson','[]') WHERE id=2");
            assertEquals(0L,functionDirectory.list(TestResource.FUNCTION,"Empty",1,null,null).get("total"));
            db.execute("CREATE TABLE rule_mock_rule(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id BIGINT,seed_key VARCHAR(32),name VARCHAR(128),value_type VARCHAR(24),match_names VARCHAR(512),generator VARCHAR(24),config_json JSON)");
            var catalog = new TestDataCatalog(db,permissions,null,projects);
            catalog.saveRule(null,new MockRule(null,"全局金额","NUMBER","amount","FIXED","10",false));
            catalog.saveRule(null,new MockRule(null,"项目金额","NUMBER","amount","FIXED","20",false,22L,null));
            assertEquals(2,catalog.rulesForProject(22L).size());
            assertEquals("项目金额",catalog.rulesForProject(22L).get(0).name());
            assertEquals(1,catalog.rulesForProject(23L).size());
            assertEquals(1,catalog.rulesForProject(null).size());
            db.execute("ALTER TABLE rule_api_test_case ADD params_json JSON");
            db.update("UPDATE rule_api_test_case SET params_json=? WHERE id=12","{\"profile\":{\"score\":9007199254740993}}");
            String binding = "{\"type\":\"API\",\"id\":\"12\",\"pointer\":\"/profile/score\"}";
            assertEquals(9007199254740993L,catalog.caseValue(binding,"LONG",22L).longValue());
            assertFalse(catalog.validExample("CASE",null,"OBJECT",22L,binding));
            assertFalse(catalog.validExample("CASE",null,"LONG",23L,binding));
            db.update("UPDATE rule_api_test_case SET params_json=? WHERE id=12","{\"profile\":{\"score\":0}}");
            assertEquals(0L,catalog.caseValue(binding,"LONG",22L).longValue());
            db.update("DELETE FROM rule_api_test_case WHERE id=12");
            assertFalse(catalog.validExample("CASE",null,"LONG",22L,binding));
            assertFalse(catalog.compatible(catalog.rulesForProject(22L).get(0).id(),"NUMBER",23L));
            assertThrows(IllegalArgumentException.class,() -> catalog.saveRule(catalog.rulesForProject(22L).get(0).id(),
                    new MockRule(null,"改为全局","NUMBER","amount","FIXED","42",false)));
        } finally { admin.execute("DROP DATABASE "+dbName); }
    }
}
