package com.hengshucredit.rule.server.config;

import com.hengshucredit.rule.model.entity.RuleListRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

public class JacksonConfigTest {

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

    private ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().localDateTimeCustomizer().customize(builder);
        return builder.build();
    }
}
