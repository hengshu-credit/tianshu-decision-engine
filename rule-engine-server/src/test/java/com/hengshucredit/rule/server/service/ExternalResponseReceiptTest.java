package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExternalResponseReceiptTest {
    @Test public void largeRepeatedPayloadsRoundTripWithoutCollidingWithProtocolFields() {
        Map<String,Object> payload = new LinkedHashMap<>();
        for (int i=0; i<8000; i++) payload.put("field." + i, i);
        String raw = JSON.toJSONString(payload);
        var body = new LinkedHashMap<String,Object>();
        body.put("data", payload); body.put("nullField", null); body.put("r1:", List.of(0));
        var response = Map.of("body", body, "steps", List.of(body, Map.of("data", new LinkedHashMap<>(payload))),
                "rawResponseBody", raw, "originalResponseBody", raw);
        String stored = ExternalResponseReceipt.encode(response);
        assertTrue(stored.startsWith("r1:"));
        assertEquals(response, ExternalResponseReceipt.decode(stored));
        assertTrue(stored.length() < raw.length());
        assertEquals(payload, ExternalResponseReceipt.decode(ExternalResponseReceipt.encode(payload)));
        assertEquals(Map.of("old", 1), ExternalResponseReceipt.decode("{\"old\":1}"));
    }
}
