package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;

public class RuntimeCallLogContentPolicyTest {
    @Test
    public void disabledContentDropsPayloadFieldsButKeepsRuntimeMetadata() {
        ExternalCallProperties properties = new ExternalCallProperties();
        properties.setRuntimeLogContentEnabled(false);
        RuntimeCallLogContentPolicy policy = new RuntimeCallLogContentPolicy();
        ReflectionTestUtils.setField(policy, "properties", properties);
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        log.setTargetCode("risk_db");
        log.setRequestBody("SELECT 1");
        log.setResponseBody("[{\"score\":88}]");

        policy.apply(log);

        assertEquals("risk_db", log.getTargetCode());
        assertNull(log.getRequestBody());
        assertNull(log.getResponseBody());
    }

    @Test
    public void oversizedContentFieldIsOmitted() {
        ExternalCallProperties properties = new ExternalCallProperties();
        properties.setRuntimeLogMaxFieldBytes(4);
        RuntimeCallLogContentPolicy policy = new RuntimeCallLogContentPolicy();
        ReflectionTestUtils.setField(policy, "properties", properties);
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        log.setRequestBody("SELECT 1");
        log.setResponseBody("ok");

        policy.apply(log);

        assertNull(log.getRequestBody());
        assertEquals("ok", log.getResponseBody());
    }
}
