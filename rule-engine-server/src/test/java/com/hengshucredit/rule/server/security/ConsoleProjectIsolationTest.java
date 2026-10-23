package com.hengshucredit.rule.server.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableOptionMapper;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class ConsoleProjectIsolationTest {
    private JdbcTemplate jdbc;
    private RuleVariableMapper variables;
    private RuleVariableOptionMapper options;
    private ConsoleDataPermissionHandler policy;
    private ConsoleProjectAccess access;
    private SqlSessionTemplate sessions;
    private ConsoleProjectAccess.Scope scope;

    @Before public void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:project" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        for (String table : List.of("rule_project", "rule_variable", "rule_variable_option", "governed_resource", "governed_resource_version",
                "rule_definition", "rule_definition_ref", "rule_regression_evidence", "rule_regression_evidence_latest")) {
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
            String ddl = schema.substring(start, schema.indexOf(';', start) + 1).replaceFirst("\\) ENGINE=.*;", ")");
            // MySQL index names are table-local; H2 requires schema-wide uniqueness.
            ddl = ddl.replaceAll("(?i)(\\b(?:KEY|INDEX)\\s+`)([^`]+)(`)", "$1" + table + "_$2$3");
            jdbc.execute(ddl);
        }
        jdbc.update("INSERT INTO rule_project(id,project_code,project_name) VALUES (1,'A','Project A'),(2,'B','Project B')");
        jdbc.update("INSERT INTO rule_variable(id,project_id,scope,var_code,var_label,var_type,var_source) VALUES (10,0,'GLOBAL','global','Global','STRING','INPUT'),(11,1,'PROJECT','a','A','STRING','INPUT'),(12,2,'PROJECT','b','B','STRING','INPUT')");
        jdbc.update("INSERT INTO rule_variable_option(id,variable_id,option_value,option_label) VALUES (1,11,'a','A'),(2,12,'b','B')");
        access = new ConsoleProjectAccess() { @Override public Scope current() { return scope; } };
        policy = new ConsoleDataPermissionHandler(access);
        var plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new ConsoleDataWriteInterceptor(access, policy, jdbc));
        plugins.addInnerInterceptor(new ConsoleDataPermissionInterceptor(policy));
        plugins.addInnerInterceptor(new PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.H2));
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); config.setLogImpl(NoLoggingImpl.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(dataSource); factory.setConfiguration(config);
        factory.setGlobalConfig(new GlobalConfig().setBanner(false).setMetaObjectHandler(new com.hengshucredit.rule.server.config.MybatisPlusConfig().metaObjectHandler())); factory.setPlugins(plugins);
        var sf = factory.getObject(); config.addMapper(RuleVariableMapper.class); config.addMapper(RuleVariableOptionMapper.class);
        config.addMapper(com.hengshucredit.rule.server.mapper.GovernedResourceMapper.class);
        config.addMapper(com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper.class);
        config.addMapper(com.hengshucredit.rule.server.mapper.RuleRegressionEvidenceMapper.class);
        sessions = new SqlSessionTemplate(sf); variables = sessions.getMapper(RuleVariableMapper.class); options = sessions.getMapper(RuleVariableOptionMapper.class);
    }
    @After public void cleanup() { if (jdbc != null) jdbc.execute("SHUTDOWN"); }

    @Test public void regressionReportsRequireBothRuleAndExecutionProjectVisibility() {
        jdbc.update("INSERT INTO rule_definition(id,project_id,scope,rule_code,rule_name,model_type) VALUES (21,1,'PROJECT','RA','RA','SCRIPT'),(22,2,'PROJECT','RB','RB','SCRIPT'),(23,0,'GLOBAL','RG','RG','SCRIPT')");
        for (long[] row : List.of(new long[]{31,21,1}, new long[]{32,22,2}, new long[]{33,23,2}, new long[]{34,23,0})) {
            jdbc.update("INSERT INTO rule_regression_evidence(id,definition_id,scenario_id,scenario_name,revision_id,project_id,scenario_digest,eligible,state,create_time) VALUES (?,?,?,?,1,?,'digest',1,'RUNNING',CURRENT_TIMESTAMP)", row[0], row[1], row[0], "case" + row[0], row[2]);
            jdbc.update("INSERT INTO rule_regression_evidence_latest(definition_id,revision_id,scenario_id,evidence_id) VALUES (?,1,?,?)", row[1], row[0], row[0]);
        }
        permissions(false, Set.of(1L), true, Set.of("rule:view", "rule:edit"), Map.of());
        var reports = sessions.getMapper(com.hengshucredit.rule.server.mapper.RuleRegressionEvidenceMapper.class);
        Assert.assertEquals(Set.of(31L,34L), reports.selectList(null).stream().map(com.hengshucredit.rule.model.entity.RuleRegressionEvidence::getId).collect(java.util.stream.Collectors.toSet()));
        Assert.assertEquals(List.of(34L), reports.latestByCase(23L,1L).stream().map(com.hengshucredit.rule.model.entity.RuleRegressionEvidence::getId).toList());
        var forbidden = new com.hengshucredit.rule.model.entity.RuleRegressionEvidence();
        forbidden.setDefinitionId(23L); forbidden.setProjectId(2L);
        forbidden.setScenarioId(99L); forbidden.setScenarioName("forbidden"); forbidden.setRevisionId(1L);
        forbidden.setScenarioDigest("digest"); forbidden.setEligible(1); forbidden.setState("RUNNING"); forbidden.setCreateTime(java.time.LocalDateTime.now());
        Throwable failure = Assert.assertThrows(RuntimeException.class, () -> reports.insert(forbidden));
        while (failure.getCause() != null) failure = failure.getCause();
        Assert.assertTrue(failure instanceof ConsoleAccessDeniedException);
        Assert.assertEquals(Integer.valueOf(4), jdbc.queryForObject("SELECT COUNT(*) FROM rule_regression_evidence", Integer.class));
    }
    private Set<Long> visibleIds() { return variables.selectList(null).stream().map(RuleVariable::getId).collect(java.util.stream.Collectors.toSet()); }
    private void permissions(boolean all, Set<Long> ids, boolean globalEditable, Set<String> defaults, Map<Long, Map<String, String>> overrides) {
        scope = new ConsoleProjectAccess.Scope(7L, ids, defaults, all, globalEditable, overrides);
    }

    @Test public void defaultsApplyToEveryProjectUnlessOverridden() {
        permissions(true, Set.of(), true, Set.of("field:view"), Map.of());
        assertEquals(Set.of(10L,11L,12L), visibleIds());
        permissions(true, Set.of(), true, Set.of("field:view"), Map.of(2L, Map.of("field:view", "DENY")));
        assertEquals(Set.of(10L,11L), visibleIds());
        assertNull(variables.selectById(12L));
    }
    @Test public void selectedEmptyMeansOnlyGlobalAndNoModuleMeansNoGlobal() {
        permissions(false, Set.of(), true, Set.of("field:view"), Map.of());
        assertEquals(Set.of(10L), visibleIds());
        permissions(true, Set.of(), true, Set.of(), Map.of());
        assertTrue(visibleIds().isEmpty());
    }
    @Test public void projectAllowDoesNotGrantGlobalOrOtherProjects() {
        permissions(true, Set.of(), true, Set.of(), Map.of(1L, Map.of("field:view", "ALLOW")));
        assertEquals(Set.of(11L), visibleIds());
        assertTrue(scope.availablePermissions().contains("field:view"));
        assertFalse(scope.globalCan("field:view"));
    }
    @Test public void paginationTotalsAndChildIdsCannotEscapeSelectedProject() {
        permissions(false, Set.of(1L), true, Set.of("field:view"), Map.of(2L, Map.of("field:view", "ALLOW")));
        var page = variables.selectPage(new Page<>(1,1), null);
        assertEquals(2L, page.getTotal());
        assertNull(variables.selectById(12L));
        assertEquals(1, options.selectList(null).size());
        assertNull(options.selectById(2L));
    }
    @Test public void projectReadOnlyBlocksDirectWriteAndDelete() {
        permissions(true, Set.of(), true, Set.of("field:view", "field:edit"), Map.of(2L, Map.of("field:edit", "DENY")));
        var b = variables.selectById(12L); b.setVarLabel("forbidden");
        assertThrows(RuntimeException.class, () -> variables.updateById(b));
        assertEquals(0, variables.deleteById(12L));
        assertEquals("B", jdbc.queryForObject("SELECT var_label FROM rule_variable WHERE id=12", String.class));
        var a = variables.selectById(11L); a.setVarLabel("allowed"); assertEquals(1,variables.updateById(a));
    }
    @Test public void globalReadOnlyDoesNotDisableProjectEditing() {
        permissions(true, Set.of(), false, Set.of("field:view", "field:edit"), Map.of());
        var global = variables.selectById(10L); global.setVarLabel("forbidden");
        assertThrows(RuntimeException.class, () -> variables.updateById(global));
        var a = variables.selectById(11L); a.setVarLabel("allowed"); assertEquals(1,variables.updateById(a));
        assertEquals("Global", jdbc.queryForObject("SELECT var_label FROM rule_variable WHERE id=10", String.class));
    }
    @Test public void movingGlobalResourceIntoUnauthorizedProjectIsRejected() {
        permissions(false, Set.of(1L), true, Set.of("field:view", "field:edit"), Map.of());
        var global = variables.selectById(10L); global.setScope("PROJECT"); global.setProjectId(2L);
        assertThrows(RuntimeException.class, () -> variables.updateById(global));
        assertEquals("GLOBAL", jdbc.queryForObject("SELECT scope FROM rule_variable WHERE id=10", String.class));
    }
    @Test public void jdbcCteStatisticsUseSameScope() {
        permissions(false, Set.of(1L), true, Set.of("field:view"), Map.of());
        String sql = policy.filterSql("WITH visible AS (SELECT id FROM rule_variable) SELECT COUNT(*) FROM visible");
        assertEquals(Long.valueOf(2), jdbc.queryForObject(sql,Long.class));
    }
    @Test public void httpTargetChecksProjectActionBeforeExecutingTheController() {
        permissions(true, Set.of(), false, Set.of("field:view", "field:edit"), Map.of(2L, Map.of("field:edit", "DENY")));
        var guard = new ConsoleTargetAuthorization(access, new ConsoleResourceAccess(access, policy, jdbc));
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/rule/variable/12/test");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("variableId", "12"));
        request.setAttribute(ConsolePermissionInterceptor.AUTHORIZED_PERMISSION, "field:edit");
        assertThrows(ConsoleAccessDeniedException.class, () -> guard.preHandle(request, new org.springframework.mock.web.MockHttpServletResponse(), null));
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("variableId", "11"));
        assertTrue(guard.preHandle(request, new org.springframework.mock.web.MockHttpServletResponse(), null));
    }
    @Test public void unauthorizedDetailIdIsRejectedEvenWhenModuleMenuIsAllowed() {
        permissions(false, Set.of(1L), true, Set.of("field:view"), Map.of());
        var guard = new ConsoleTargetAuthorization(access, new ConsoleResourceAccess(access, policy, jdbc));
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/rule/variable/12");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "12"));
        request.setAttribute(ConsolePermissionInterceptor.AUTHORIZED_PERMISSION, "field:view");
        assertThrows(ConsoleAccessDeniedException.class, () -> guard.preHandle(request, new org.springframework.mock.web.MockHttpServletResponse(), null));
    }
    @Test public void logVisibilityUsesStableProjectIdNotMutableCode() {
        jdbc.execute("CREATE TABLE rule_execution_log(id BIGINT, execution_project_id BIGINT, project_code VARCHAR(64), create_time DATETIME)");
        jdbc.update("INSERT INTO rule_execution_log VALUES(1,1,'Old-A',CURRENT_TIMESTAMP),(2,2,'A',CURRENT_TIMESTAMP),(3,NULL,'A',CURRENT_TIMESTAMP),(4,NULL,NULL,CURRENT_TIMESTAMP)");
        permissions(false, Set.of(1L), true, Set.of("log:view"), Map.of());
        assertEquals(List.of(1L,4L), jdbc.queryForList(policy.filterSql("SELECT id FROM rule_execution_log ORDER BY id"),Long.class));
    }
    @Test public void fieldEditorCanValidateProjectMembershipWithoutReadingProjectConfiguration() {
        jdbc.update("INSERT INTO governed_resource(id,resource_type,resource_id,project_id,effective_version_id,effective_version_no,effective_status) VALUES(100,'PROJECT',1,1,101,1,'ACTIVE')");
        jdbc.update("INSERT INTO governed_resource_version(id,governed_resource_id,resource_type,resource_id,version_no,snapshot_json,snapshot_digest,effective_status) VALUES(101,100,'PROJECT',1,1,'{\"description\":\"private project configuration\"}',?,'ACTIVE')", "a".repeat(64));
        permissions(false, Set.of(1L), true, Set.of("field:view", "field:edit"), Map.of());
        var resources = sessions.getMapper(com.hengshucredit.rule.server.mapper.GovernedResourceMapper.class);
        var versions = sessions.getMapper(com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper.class);
        var dependencies = new com.hengshucredit.rule.server.governance.GovernanceDependencyService();
        org.springframework.test.util.ReflectionTestUtils.setField(dependencies, "resourceMapper", resources);
        org.springframework.test.util.ReflectionTestUtils.setField(dependencies, "versionMapper", versions);
        org.springframework.test.util.ReflectionTestUtils.setField(dependencies, "consoleProjects", access);
        var report = dependencies.preflight("VARIABLE", 11L, List.of(new com.hengshucredit.rule.server.governance.ResourceDependencyRef(
                "PROJECT", 1L, "PROJECT", "$.projectId", "REFERENCE", true)), List.of());
        assertTrue(report.errors().toString(), report.valid());
        assertNull(versions.selectById(101L));
        assertFalse(com.alibaba.fastjson.JSON.toJSONString(report).contains("private project configuration"));
    }
    @Test public void readOnlyUserCanInitializeLegacyVersionMetadataWithoutEditingContent() {
        permissions(true, Set.of(), false, Set.of("field:view"), Map.of());
        var adapter = com.hengshucredit.rule.server.governance.GovernedResourceAdapter.class.cast(java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{com.hengshucredit.rule.server.governance.GovernedResourceAdapter.class}, (target, method, args) -> switch (method.getName()) {
                    case "resourceType" -> "VARIABLE";
                    case "loadEffective" -> com.hengshucredit.rule.server.governance.ResourceSnapshot.ofJson("{\"id\":10,\"scope\":\"GLOBAL\",\"projectId\":0}");
                    default -> throw new UnsupportedOperationException(method.getName());
                }));
        var bootstrap = new com.hengshucredit.rule.server.governance.GovernanceResourceBootstrapService();
        org.springframework.test.util.ReflectionTestUtils.setField(bootstrap, "resourceMapper", sessions.getMapper(com.hengshucredit.rule.server.mapper.GovernedResourceMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(bootstrap, "versionMapper", sessions.getMapper(com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(bootstrap, "adapterRegistry", new com.hengshucredit.rule.server.governance.GovernedResourceAdapterRegistry(List.of(adapter)));
        var result = bootstrap.ensure("VARIABLE", 10L);
        assertNotNull(result.getEffectiveVersionId());
        assertEquals(result.getEffectiveVersionId(), jdbc.queryForObject("SELECT effective_version_id FROM governed_resource WHERE resource_type='VARIABLE' AND resource_id=10", Long.class));
        assertEquals("Global", jdbc.queryForObject("SELECT var_label FROM rule_variable WHERE id=10", String.class));
    }
}
