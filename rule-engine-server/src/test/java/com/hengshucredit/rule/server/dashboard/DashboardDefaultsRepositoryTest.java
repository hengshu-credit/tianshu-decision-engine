package com.hengshucredit.rule.server.dashboard;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.Assert.*;

public class DashboardDefaultsRepositoryTest {
    @Test
    public void defaultsPersistAcrossInstancesAndRejectStaleSaves() throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:dashboard" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        try (var stream = getClass().getResourceAsStream("/sql/schema.sql")) {
            String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `rule_dashboard_default`");
            assertTrue(start >= 0);
            jdbc.execute(schema.substring(start, schema.indexOf(';', start) + 1));
        }
        var repository = new MybatisDashboardMappingRepository(null, null, null, jdbc);
        assertEquals(0, repository.findSystemDefaults().version());
        String mapping = "{\"AMOUNT\":{\"refType\":\"VARIABLE\",\"refId\":9001,\"jsonSource\":\"INPUT\"}}";
        repository.saveSystemDefaults(mapping, 0L, "admin");
        var reloaded = new MybatisDashboardMappingRepository(null, null, null, jdbc);
        assertEquals(mapping, reloaded.findSystemDefaults().mappingsJson());
        assertEquals(1, reloaded.findSystemDefaults().version());
        assertThrows(IllegalArgumentException.class, () -> reloaded.saveSystemDefaults("{}", 0L, "stale"));
        reloaded.saveSystemDefaults("{}", 1L, "admin");
        assertEquals(2, reloaded.findSystemDefaults().version());
        assertThrows(IllegalArgumentException.class, () -> repository.saveSystemDefaults(mapping, 1L, "stale"));
        assertEquals("{}", reloaded.findSystemDefaults().mappingsJson());
    }
}
