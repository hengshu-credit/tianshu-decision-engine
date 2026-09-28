package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class RuntimeCallLogAsyncWriterPayloadTest {

    @Test
    public void asyncCopyPreservesCallIdAndRawPayloads() throws Exception {
        RuntimeCallLogAsyncWriter writer = new RuntimeCallLogAsyncWriter(
                new ExternalCallProperties(), (SqlSessionFactory) null);
        RuleRuntimeCallLog source = new RuleRuntimeCallLog();
        source.setCallId("call-1");
        source.setRawRequestBody("{\"age\":18}");
        source.setOriginalRequestBody("{\"age\":18,\"encrypted\":true}");
        source.setRawRequestMetadata("{\"status\":\"FILTERED\"}");
        source.setRawResponseBody("{\"score\":720}");
        source.setOriginalResponseBody("{\"ciphertext\":\"abc\"}");
        source.setRawResponseMetadata("{\"status\":\"DECRYPTED\"}");

        Method copy = RuntimeCallLogAsyncWriter.class.getDeclaredMethod("copy", RuleRuntimeCallLog.class);
        copy.setAccessible(true);
        RuleRuntimeCallLog copied = (RuleRuntimeCallLog) copy.invoke(writer, source);

        assertEquals("call-1", copied.getCallId());
        assertEquals(source.getRawRequestBody(), copied.getRawRequestBody());
        assertEquals(source.getOriginalRequestBody(), copied.getOriginalRequestBody());
        assertEquals(source.getRawRequestMetadata(), copied.getRawRequestMetadata());
        assertEquals(source.getRawResponseBody(), copied.getRawResponseBody());
        assertEquals(source.getOriginalResponseBody(), copied.getOriginalResponseBody());
        assertEquals(source.getRawResponseMetadata(), copied.getRawResponseMetadata());
    }
}
