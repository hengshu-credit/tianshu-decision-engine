package com.hengshucredit.rule.client;

import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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

    @Test
    public void legacyBuilderTokenRemainsDirectAndDoesNotEnableExchange() throws Exception {
        RuleEngineClientConfig config = new RuleEngineClientConfig();
        config.setToken("legacy-token");

        ClientAuthConfig auth = resolveAuthConfig(config);

        assertEquals(ClientAuthConfig.LEGACY_TOKEN, auth.getAuthType());
        assertEquals("legacy-token", auth.getLegacyToken());
        assertFalse(auth.isTokenExchangeEnabled());
    }

    private String resolvePushSubscriptionKey(RuleEngineClientConfig config) throws Exception {
        Method method = RuleEngineClient.class.getDeclaredMethod("resolvePushSubscriptionKey", RuleEngineClientConfig.class);
        method.setAccessible(true);
        return (String) method.invoke(null, config);
    }

    private ClientAuthConfig resolveAuthConfig(RuleEngineClientConfig config) throws Exception {
        Method method = RuleEngineClient.class.getDeclaredMethod("resolveAuthConfig", RuleEngineClientConfig.class);
        method.setAccessible(true);
        return (ClientAuthConfig) method.invoke(null, config);
    }
}
