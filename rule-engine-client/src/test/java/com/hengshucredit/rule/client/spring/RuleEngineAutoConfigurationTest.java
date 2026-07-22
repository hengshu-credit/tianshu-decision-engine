package com.hengshucredit.rule.client.spring;

import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleEngineAutoConfigurationTest {

    @Test
    public void registersAutoConfigurationWithSpringBoot3Imports() throws Exception {
        String resource = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        Enumeration<URL> resources = RuleEngineAutoConfigurationTest.class.getClassLoader().getResources(resource);
        boolean registered = false;
        while (resources.hasMoreElements()) {
            try (InputStream input = resources.nextElement().openStream()) {
                String imports = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));
                registered = registered || imports.contains(RuleEngineAutoConfiguration.class.getName());
            }
        }

        assertTrue("RuleEngineAutoConfiguration is not registered for Spring Boot 3", registered);
    }

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
