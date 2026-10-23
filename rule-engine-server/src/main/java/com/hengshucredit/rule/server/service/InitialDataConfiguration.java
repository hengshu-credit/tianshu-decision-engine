package com.hengshucredit.rule.server.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InitialDataProperties.class)
public class InitialDataConfiguration {
}
