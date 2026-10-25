package com.hengshucredit.rule.runtime;

import org.junit.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.Assert.assertEquals;

public class RuntimeDeploymentConfigurationTest {
    @Test
    public void runtimeDefaultsToSdkAndHonorsExplicitPort() {
        MockEnvironment env = environment("sdk");
        assertEquals("sdk", env.getProperty("spring.profiles.default"));
        assertEquals("7071", env.getProperty("server.port"));
        env.setProperty("SERVER_PORT", "27071");
        assertEquals("27071", env.getProperty("server.port"));
        org.junit.Assert.assertFalse(new ClassPathResource("application-http.yml").exists());
    }

    @Test
    public void sdkProfileMapsItsOwnTokenProjectAndRedisConnection() {
        MockEnvironment env = environment("sdk")
                .withProperty("SDK_ACCESS_TOKEN", "sdk-token")
                .withProperty("SDK_RULE_ALLOWED_CODES", "SDK_RULE")
                .withProperty("RUNTIME_RULE_PROJECT_ID", "123")
                .withProperty("RUNTIME_RULE_PROJECT_CODE", "project-A")
                .withProperty("RUNTIME_SDK_PORT", "17071")
                .withProperty("REDIS_HOST", "shared-redis")
                .withProperty("REDIS_PORT", "16379")
                .withProperty("REDIS_PASSWORD", "redis-password")
                .withProperty("REDIS_DATABASE", "3");

        assertEquals("sdk-token", env.getProperty("RUNTIME_ACCESS_TOKEN"));
        assertEquals("SDK_RULE", env.getProperty("RULE_ALLOWED_CODES"));
        assertEquals("123", env.getProperty("RULE_PROJECT_ID"));
        assertEquals("project-A", env.getProperty("RULE_PROJECT_CODE"));
        assertEquals("17071", env.getProperty("server.port"));
        assertEquals("shared-redis", env.getProperty("spring.data.redis.host"));
        assertEquals("16379", env.getProperty("spring.data.redis.port"));
        assertEquals("redis-password", env.getProperty("spring.data.redis.password"));
        assertEquals("3", env.getProperty("spring.data.redis.database"));
    }

    private MockEnvironment environment(String profile) {
        MockEnvironment env = new MockEnvironment();
        for (String resource : new String[]{"application-" + profile + ".yml", "application.yml"}) {
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ClassPathResource(resource));
            env.getPropertySources().addLast(new PropertiesPropertySource(resource, yaml.getObject()));
        }
        return env;
    }
}
