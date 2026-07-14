package com.hengshucredit.rule.server.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProjectAuthProperties.class)
public class ProjectAuthConfiguration {
}
