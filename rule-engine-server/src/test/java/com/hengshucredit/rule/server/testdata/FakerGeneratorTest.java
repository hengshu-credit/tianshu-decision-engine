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
            assertEquals("全名", jdbc.queryForObject("SELECT name FROM rule_mock_rule WHERE id=?", String.class, id));
            long cityId = jdbc.queryForObject("SELECT id FROM rule_mock_rule WHERE seed_key='location.city'", Long.class);
            jdbc.update("UPDATE rule_mock_rule SET name=? WHERE id=?", "地址与区域 · 城市", cityId);
            jdbc.update("UPDATE rule_mock_rule SET name=?, match_names=? WHERE id=?", "申请人全名", "applicantName", id);
            FakerMethods.install(db);
            assertEquals(Integer.valueOf(262), jdbc.queryForObject("SELECT COUNT(*) FROM rule_mock_rule", Integer.class));
            assertEquals("城市", jdbc.queryForObject("SELECT name FROM rule_mock_rule WHERE id=?", String.class, cityId));
            assertEquals("申请人全名", jdbc.queryForObject("SELECT name FROM rule_mock_rule WHERE id=? AND match_names='applicantName'", String.class, id));
        }
    }
    @Test public void configuredArgumentsDrivePreviewAndBoundValues() {
        var rule = new MockRule(9L, "批次编码", "STRING", "", "FAKER",
                "{\"method\":\"string.alpha\",\"args\":[{\"length\":23,\"casing\":\"upper\"}]}", false);
        rule.validate();
        assertEquals("字符串", rule.getRuleType());
        assertEquals("string", rule.getRuleTypeCode());
        assertTrue(TestMockService.sample("STRING", "batch", "", 9L, null, java.util.List.of(rule)).asText().matches("[A-Z]{23}"));
        assertEquals(42, FakerGenerator.sample("number.int", MockRule.parse("[{\"min\":42,\"max\":42}]")).intValue());
        assertThrows(IllegalArgumentException.class, () -> FakerGenerator.sample("string.alpha", MockRule.parse("[{\"length\":-20}]")));
        assertThrows(IllegalArgumentException.class, () -> FakerGenerator.sample("helpers.fake", MockRule.parse("[\"{{unknown.method}}\"]")));
        assertEquals("9007199254740997", FakerGenerator.sample("number.bigInt",
                MockRule.parse("[{\"min\":\"9007199254740997\",\"max\":\"9007199254740997\"}]")).asText());
    }

    @Test public void everyDocumentedExampleAcceptsItsArguments() throws java.io.IOException {
        try (var in = getClass().getResourceAsStream("/faker-methods.json")) {
            var groups = MockRule.JSON.readTree(in);
            int count = 0;
            for (var group : groups) {
                for (var method : group.path("methods")) {
                    if (!method.has("parameters")) continue;
                    var key = group.path("module").asText() + "." + method.path("name").asText();
                    var config = MockRule.JSON.createObjectNode().put("method", key).set("args", method.get("exampleArgs"));
                    var rule = new MockRule(null, key, method.path("type").asText(), "", "FAKER", config.toString(), false);
                    try {
                        rule.validate();
                        assertNotNull(rule.sample());
                    } catch (RuntimeException ex) { throw new AssertionError(key, ex); }
                    count++;
                }
            }
            assertEquals(126, count);
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
