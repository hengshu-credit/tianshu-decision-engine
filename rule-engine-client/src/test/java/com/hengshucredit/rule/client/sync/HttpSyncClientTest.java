package com.hengshucredit.rule.client.sync;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.client.cache.CachedRule;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class HttpSyncClientTest {

    @Test
    public void mapsRootOutputScriptNamesFromSyncPayload() throws Exception {
        HttpSyncClient client = new HttpSyncClient("http://localhost:8080", 1000);
        Method method = HttpSyncClient.class.getDeclaredMethod(
                "toCachedRule", com.alibaba.fastjson.JSONObject.class);
        method.setAccessible(true);

        CachedRule rule = (CachedRule) method.invoke(client, JSON.parseObject("{"
                + "\"ruleCode\":\"ROOT\","
                + "\"outputScriptNames\":[\"decision\",\"notAssigned\"]"
                + "}"));

        assertEquals(Arrays.asList("decision", "notAssigned"), rule.getOutputScriptNames());
    }
}
