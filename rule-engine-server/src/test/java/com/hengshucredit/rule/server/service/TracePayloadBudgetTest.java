package com.hengshucredit.rule.server.service;

import org.junit.Assert;
import org.junit.Test;

public class TracePayloadBudgetTest {
    @Test
    public void keepsSmallPayloadsUnchanged() {
        Assert.assertEquals("{}", TracePayloadBudget.limit("{}", 2));
    }

    @Test
    public void replacesOversizedPayloadWithExplicitTruncationMarker() {
        String result = TracePayloadBudget.limit("{\"value\":\"这是很长的追踪内容\"}", 8);

        Assert.assertTrue(result.contains("\"truncated\":true"));
        Assert.assertTrue(result.contains("originalBytes"));
        Assert.assertTrue(result.contains("maxBytes"));
        Assert.assertTrue(result.contains("请求结果未截断"));
    }
}
