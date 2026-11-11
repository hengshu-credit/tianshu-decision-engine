package com.hengshucredit.rule.server.security;

import com.hengshucredit.rule.server.sql.TestSchemaTables;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.config.JacksonConfig;
import org.junit.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.Assert.*;

public class ConsoleRecordIsolationTest {
    @Test
    public void disablingConsoleLoginDoesNotExposeAccountRecordsAnonymously() {
        var access = new ConsoleProjectAccess();
        var properties = new com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties();
        properties.setEnabled(false);
        ReflectionTestUtils.setField(access, "properties", properties);
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/rule/console/operation-logs");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            assertNotNull(access.current());
            assertNull(access.current().userId());
            assertFalse(access.current().globalCan("account:cross-record-view"));
            request.setRequestURI("/api/rule/runtime/executions/example");
            assertNull(access.current());
        } finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }
    }
    private ConsoleProjectAccess.Scope scope(boolean cross) {
        return new ConsoleProjectAccess.Scope(8L, Set.of(9L), cross
                ? Set.of("log:view", "field:view", "approval:view", "account:audit", "account:cross-record-view")
                : Set.of("log:view", "field:view", "approval:view", "account:audit"), false, true,
                Map.of(9L, Map.of("account:cross-record-view", "ALLOW")));
    }
    private ConsoleDataPermissionHandler handler(ConsoleProjectAccess.Scope scope) {
        return new ConsoleDataPermissionHandler(new ConsoleProjectAccess() {
            @Override public Scope current() { return scope; }
        });
    }
    @Test
    public void listsCountsAndDirectIdsApplyAccountAndProjectRestrictionsTogether() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:records" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE rule_execution_log (id BIGINT, actor_user_id BIGINT, execution_project_id BIGINT, project_code VARCHAR(32))");
        jdbc.execute("INSERT INTO rule_execution_log VALUES (1,8,9,'P9'),(2,7,9,'P9'),(3,8,10,'P10'),(4,NULL,9,'P9')");
        var own = handler(scope(false));
        assertEquals(List.of(1L), jdbc.queryForList(own.filterSql("SELECT id FROM rule_execution_log ORDER BY id"), Long.class));
        assertEquals(Long.valueOf(1), jdbc.queryForObject(own.filterSql("SELECT COUNT(*) FROM rule_execution_log"), Long.class));
        assertTrue(jdbc.queryForList(own.filterSql("SELECT id FROM rule_execution_log WHERE id=2"), Long.class).isEmpty());
        assertEquals(List.of(1L, 2L, 4L), jdbc.queryForList(handler(scope(true)).filterSql("SELECT id FROM rule_execution_log ORDER BY id"), Long.class));
        assertFalse(scope(false).can("account:cross-record-view", 9L));
    }
    @Test
    public void approvalsAndOperationHistoryDoNotBecomeSharedThroughModulePermissions() throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:approvals" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE governance_approval_request (id BIGINT PRIMARY KEY, actor_user_id BIGINT, resource_type VARCHAR(32), project_id BIGINT)");
        jdbc.execute("INSERT INTO governance_approval_request VALUES (1,8,'VARIABLE',9),(2,7,'VARIABLE',9)");
        jdbc.execute("CREATE TABLE console_operation_log (id BIGINT,user_id BIGINT,module VARCHAR(32),target_type VARCHAR(32),target_id VARCHAR(32))");
        jdbc.execute("INSERT INTO console_operation_log VALUES (1,8,'account','ACCOUNT','8'),(2,7,'account','ACCOUNT','7')");
        @SuppressWarnings("unchecked") var tables = (Map<String, String>) ReflectionTestUtils.getField(ConsoleDataPermissionHandler.class, "RESOURCE_TABLES");
        var referencedTables = new java.util.HashSet<>(tables.values()); referencedTables.add("rule_revision"); referencedTables.add("governed_resource"); referencedTables.add("rule_definition_version");
        TestSchemaTables.create(jdbc, referencedTables);
        var own = handler(scope(false));
        assertEquals(List.of(1L), jdbc.queryForList(own.filterSql("SELECT id FROM governance_approval_request"), Long.class));
        assertEquals(List.of(1L), jdbc.queryForList(own.filterSql("SELECT id FROM console_operation_log"), Long.class));
    }
    @Test
    public void identityCannotBeInjectedByJsonAndSurvivesBackgroundPersistence() throws Exception {
        var builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().localDateTimeCustomizer().customize(builder);
        var mapper = builder.build();
        RuleExecutionLog log = mapper.readValue("{\"actorUserId\":999,\"ruleCode\":\"test\"}", RuleExecutionLog.class);
        assertNull(log.getActorUserId());
        assertNull(mapper.readValue("{\"systemConstant\":true}", RuleVariable.class).getSystemConstant());
        var ownership = new ConsoleRecordOwnership();
        var current = new java.util.concurrent.atomic.AtomicReference<>(scope(false));
        ReflectionTestUtils.setField(ownership, "access", new ConsoleProjectAccess() {
            @Override public Scope current() { return current.get(); }
        });
        ownership.capture(log);
        assertEquals(Long.valueOf(8), log.getActorUserId());
        current.set(null);
        ownership.capture(log);
        assertEquals(Long.valueOf(8), log.getActorUserId());
        assertTrue(mapper.writeValueAsString(log).contains("\"actorUserId\":8"));
    }
}
