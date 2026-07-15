package com.hengshucredit.rule.client.spring;

import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleEngineAutoConfigurationTest {

    @Test
    public void multiAuthUsesHttpReporterForServerVerifiedAttribution() {
        RuleEngineClientProperties properties = new RuleEngineClientProperties();
        properties.setAuthType(ClientAuthConfig.BASIC);

        assertFalse(RuleEngineAutoConfiguration.shouldUseExternalReporter(properties));
    }

    @Test
    public void legacyConfigurationUsesHttpReporterForTrustedAttribution() {
        RuleEngineClientProperties properties = new RuleEngineClientProperties();
        properties.setToken("legacy-token");

        assertFalse(RuleEngineAutoConfiguration.shouldUseExternalReporter(properties));
    }

    @Test
    public void unauthenticatedConfigurationMayUseExternalReporter() {
        assertTrue(RuleEngineAutoConfiguration.shouldUseExternalReporter(new RuleEngineClientProperties()));
    }
}
