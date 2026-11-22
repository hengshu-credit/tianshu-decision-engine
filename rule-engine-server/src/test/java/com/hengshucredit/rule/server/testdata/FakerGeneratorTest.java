package com.hengshucredit.rule.server.testdata;

import org.junit.Test;
import static org.junit.Assert.*;

public class FakerGeneratorTest {
    @Test public void everyPublishedMethodProducesItsDeclaredJsonType() {
        assertEquals(262, FakerMethods.METHODS.size());
        for (var method : FakerMethods.METHODS.values()) {
            var rule = new MockRule(1L, method.label(), method.type(), "", "FAKER",
                    MockRule.JSON.getNodeFactory().textNode(method.key()).toString(), true);
            try {
                rule.validate();
                var sample = rule.sample();
                boolean matches = switch (method.type()) {
                    case "NUMBER" -> sample.isNumber();
                    case "BOOLEAN" -> sample.isBoolean();
                    case "OBJECT" -> sample.isObject();
                    case "LIST" -> sample.isArray();
                    case "DATETIME" -> sample.isTextual() && java.time.Instant.parse(sample.textValue()) != null;
                    default -> sample.isTextual();
                };
                assertTrue(method.key() + " 输出类型应为 " + method.type(), matches);
            } catch (RuntimeException error) {
                throw new AssertionError(method.key(), error);
            }
        }
    }
    @Test public void seedInstallationPreservesIdsAndEditedRules() throws java.sql.SQLException {
        try (var db = java.sql.DriverManager.getConnection("jdbc:h2:mem:fakerSeeds;MODE=MySQL")) {
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(db, true));
            jdbc.execute("""
                    CREATE TABLE rule_mock_rule (id BIGINT AUTO_INCREMENT PRIMARY KEY, seed_key VARCHAR(32) UNIQUE,
                        name VARCHAR(128), value_type VARCHAR(24), match_names VARCHAR(512), generator VARCHAR(24), config_json VARCHAR(128))
                    """);
            FakerMethods.install(db);
            long id = jdbc.queryForObject("SELECT id FROM rule_mock_rule WHERE seed_key='person.fullName'", Long.class);
            jdbc.update("UPDATE rule_mock_rule SET name=?, match_names=? WHERE id=?", "申请人全名", "applicantName", id);
            FakerMethods.install(db);
            assertEquals(Integer.valueOf(262), jdbc.queryForObject("SELECT COUNT(*) FROM rule_mock_rule", Integer.class));
            assertEquals("申请人全名", jdbc.queryForObject("SELECT name FROM rule_mock_rule WHERE id=? AND match_names='applicantName'", String.class, id));
        }
    }
    @Test public void boundFakerMethodUsesTheSameGeneratorAsPreview() {
        var rule = new MockRule(77L, "唯一标识", "STRING", "", "FAKER", "\"string.uuid\"", true);
        var value = TestMockService.sample("STRING", "applicant.id", "", 77L, null, java.util.List.of(rule));
        assertEquals(value.textValue(), java.util.UUID.fromString(value.textValue()).toString());
        assertThrows(IllegalArgumentException.class, () -> new MockRule(null, "错误类型", "NUMBER", "", "FAKER", rule.configJson(), false).validate());
        assertThrows(IllegalArgumentException.class, () -> FakerGenerator.sample("constructor.constructor"));
    }
}
