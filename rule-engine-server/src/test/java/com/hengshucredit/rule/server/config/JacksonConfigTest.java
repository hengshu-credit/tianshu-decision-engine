package com.hengshucredit.rule.server.config;

import com.hengshucredit.rule.model.entity.RuleListRecord;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.model.dto.ResolvedField;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class JacksonConfigTest {

    @Test public void fractionalOrExponentDraftLocksCannotBeTruncatedToAValidLock() throws Exception {
        for (String raw : List.of("7.9", "7e0")) {
            var source = mapper().readValue("{\"sourceType\":\"REVISION\",\"sourceId\":\"12\",\"lockVersion\":" + raw + "}",
                    com.hengshucredit.rule.model.dto.RuleTestExecutionSource.class);
            org.junit.Assert.assertThrows("不能把非整数锁版本截断后执行草稿", IllegalArgumentException.class, () -> source.validate(null));
        }
    }

    @Test public void fractionalOrExponentSourceIdsCannotBeRoundedIntoAnotherSnapshot() throws Exception {
        for (String raw : List.of("9007199254740992.9", "9.007199254740993e15")) {
            var source = mapper().readValue("{\"sourceType\":\"VERSION\",\"sourceId\":" + raw + "}",
                    com.hengshucredit.rule.model.dto.RuleTestExecutionSource.class);
            org.junit.Assert.assertThrows("不能把非规范标识静默取整后选中其它快照", IllegalArgumentException.class, () -> source.validate(null));
        }
    }

    @Test public void testSchemaHttpResponseIncludesExactExecutionIdentity() throws Exception {
        RuleTestSchema schema = new RuleTestSchema();
        var identity = new com.hengshucredit.rule.model.dto.RuleTestExecutionIdentity();
        identity.setDefinitionId("9007199254740993"); identity.setSourceType("REVISION");
        identity.setSourceId("9007199254740995"); identity.setRevisionId("9007199254740995"); identity.setLockVersion(7);
        schema.setExecutionIdentity(identity);
        var controller = new com.hengshucredit.rule.server.controller.mgmt.RuleTestSchemaController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "ruleTestSchemaService",
                new com.hengshucredit.rule.server.service.RuleTestSchemaService() {
                    @Override public RuleTestSchema build(com.hengshucredit.rule.model.dto.RuleTestSchemaRequest request) { return schema; }
                });
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper())).build();
        String raw = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/rule/test-schema")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"RULE\",\"targetId\":\"9007199254740993\"}"))
                .andReturn().getResponse().getContentAsString();
        var actual = mapper().readTree(raw).path("data").path("executionIdentity");
        assertEquals("REVISION", actual.path("sourceType").asText());
        org.junit.Assert.assertTrue(actual.path("sourceId").isTextual());
        assertEquals("9007199254740995", actual.path("sourceId").asText());
        assertEquals("9007199254740993", actual.path("definitionId").asText());
        assertEquals(7, actual.path("lockVersion").asInt());
    }

    @Test
    public void onlyTestSchemaReferencesUseTextAndExactSampleRemainsAvailable() throws Exception {
        RuleTestSchema schema = new RuleTestSchema();
        ResolvedField field = new ResolvedField(); field.setRefId(9007199254740993L); field.setRefType("VARIABLE");
        field.setScriptName("amount"); field.setValueType("NUMBER");
        field.setDefaultValue("9007199254740993"); field.setExampleValue("9007199254740994");
        field.setValidValues("[9007199254740993,9007199254740994]"); field.setDependencies(List.of("sourceA", "sourceB"));
        schema.setInputs(List.of(field)); schema.setRuntimeNodes(java.util.Arrays.asList(field, null));
        schema.setOutputs(List.of(field));
        schema.setSampleParams(Map.of("amount", new BigInteger("9007199254740993")));
        ObjectMapper mapper = mapper();
        var tree = mapper.readTree(mapper.writeValueAsString(schema));
        org.junit.Assert.assertTrue("Schema引用ID在进入浏览器前必须是字符串", tree.path("outputs").path(0).path("refId").isTextual());
        assertEquals("9007199254740993", tree.path("outputs").path(0).path("refId").asText());
        var originalField = mapper.valueToTree(field); ((com.fasterxml.jackson.databind.node.ObjectNode) originalField).put("refId", "9007199254740993");
        assertEquals("只替换引用ID，完整保留字段元数据和null", originalField, tree.path("outputs").path(0));
        assertEquals(originalField, tree.path("inputs").path(0)); assertEquals(originalField, tree.path("runtimeNodes").path(0));
        org.junit.Assert.assertTrue(tree.path("runtimeNodes").path(1).isNull());
        assertEquals("{\"amount\":9007199254740993}", tree.path("sampleParamsJson").asText());
        assertEquals("{\"id\":9007199254740993}", mapper.writeValueAsString(Map.of("id", 9007199254740993L)));
    }

    @Test
    public void localDateTimeAcceptsSpaceSeparatedValue() throws Exception {
        ObjectMapper mapper = mapper();
        RuleListRecord record = mapper.readValue("{\"effectiveTime\":\"2026-06-22 00:00:00\"}", RuleListRecord.class);

        assertEquals(LocalDateTime.of(2026, 6, 22, 0, 0, 0), record.getEffectiveTime());
    }

    @Test
    public void localDateTimeAcceptsIsoValue() throws Exception {
        ObjectMapper mapper = mapper();
        RuleListRecord record = mapper.readValue("{\"expireTime\":\"2026-12-31T23:59:59\"}", RuleListRecord.class);

        assertEquals(LocalDateTime.of(2026, 12, 31, 23, 59, 59), record.getExpireTime());
    }

    @Test
    public void internalRuleSetStatisticsDoNotTravelThroughLogApiPayloads() throws Exception {
        var log = new com.hengshucredit.rule.model.entity.RuleExecutionLog();
        log.setTraceInfo("[]"); log.setRuleSetStatsJson("[{\"key\":\"ID:1\"}]"); log.setRuleSetStatsStatus("READY");
        var mapper = mapper();
        var tree = mapper.readTree(mapper.writeValueAsString(log));
        org.junit.Assert.assertFalse(tree.has("ruleSetStatsJson"));
        org.junit.Assert.assertFalse(tree.has("ruleSetStatsStatus"));
        assertEquals("[]", tree.get("traceInfo").asText());
        var incoming = mapper.readValue("{\"traceInfo\":\"[]\",\"ruleSetStatsStatus\":\"READY\",\"ruleSetStatsJson\":\"[]\"}", log.getClass());
        org.junit.Assert.assertNull(incoming.getRuleSetStatsJson());
        org.junit.Assert.assertNull(incoming.getRuleSetStatsStatus());
    }

    private ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().localDateTimeCustomizer().customize(builder);
        return builder.build();
    }
}
