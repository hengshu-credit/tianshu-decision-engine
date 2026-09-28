package com.hengshucredit.rule.server.health;

import org.junit.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class HealthProbeConfigurationTest {

    @Test
    public void applicationConfigExposesOnlyHealthInfoAndSeparatesProbeDependencies() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        for (PropertySource<?> source : yaml) {
            sources.addFirst(source);
        }

        assertEquals("health,info",
                environment.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("true",
                environment.getProperty("management.endpoint.health.probes.enabled"));
        assertEquals("livenessState,ping",
                environment.getProperty("management.endpoint.health.group.liveness.include"));
        assertEquals("readinessState,db,redis,onnxWarmup,ruleWarmup",
                environment.getProperty("management.endpoint.health.group.readiness.include"));
    }
}
