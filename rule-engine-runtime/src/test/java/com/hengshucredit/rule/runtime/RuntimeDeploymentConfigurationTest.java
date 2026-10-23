package com.hengshucredit.rule.runtime;

import org.junit.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.Assert.assertEquals;

public class RuntimeDeploymentConfigurationTest {
    @Test
    public void httpProfileMapsClientFileSettingsAndKeepsExplicitOverrides() {
        MockEnvironment env = environment("http")
                .withProperty("RUNTIME_RULE_SERVER_URL", "http://upstream:8080")
                .withProperty("RUNTIME_PROJECT_ACCESS_TOKEN", "project-token")
                .withProperty("HTTP_ACCESS_TOKEN", "http-token")
                .withProperty("SDK_ACCESS_TOKEN", "sdk-token")
                .withProperty("HTTP_RULE_ALLOWED_CODES", "HTTP_RULE")
                .withProperty("RUNTIME_HTTP_PORT", "17070")
                .withProperty("RUNTIME_TRACE_ENABLED", "false");

        assertEquals("http://upstream:8080", env.getProperty("RULE_SERVER_URL"));
        assertEquals("project-token", env.getProperty("PROJECT_ACCESS_TOKEN"));
        assertEquals("http-token", env.getProperty("RUNTIME_ACCESS_TOKEN"));
        assertEquals("HTTP_RULE", env.getProperty("RULE_ALLOWED_CODES"));
        assertEquals("17070", env.getProperty("server.port"));
        assertEquals("false", env.getProperty("RULE_TRACE_ENABLED"));
        env.setProperty("RUNTIME_ACCESS_TOKEN", "explicit-token");
        env.setProperty("SERVER_PORT", "27070");
        assertEquals("explicit-token", env.getProperty("RUNTIME_ACCESS_TOKEN"));
        assertEquals("27070", env.getProperty("server.port"));
    }

    @Test
    public void sdkProfileMapsItsOwnTokenProjectAndRedisConnection() {
        MockEnvironment env = environment("sdk")
                .withProperty("HTTP_ACCESS_TOKEN", "http-token")
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
