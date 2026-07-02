package com.hengshucredit.rule.client;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class RuleEngineClientConfigTest {

    @Test
    public void projectCodeIsPreferredForRedisPushSubscription() throws Exception {
        RuleEngineClientConfig config = new RuleEngineClientConfig();
        config.setAppName("risk-service");
        config.setProjectCode("credit_project");

        assertEquals("credit_project", resolvePushSubscriptionKey(config));
    }

    @Test
    public void appNameIsFallbackForRedisPushSubscription() throws Exception {
        RuleEngineClientConfig config = new RuleEngineClientConfig();
        config.setAppName("risk-service");

        assertEquals("risk-service", resolvePushSubscriptionKey(config));
    }

    private String resolvePushSubscriptionKey(RuleEngineClientConfig config) throws Exception {
        Method method = RuleEngineClient.class.getDeclaredMethod("resolvePushSubscriptionKey", RuleEngineClientConfig.class);
        method.setAccessible(true);
        return (String) method.invoke(null, config);
    }
}
