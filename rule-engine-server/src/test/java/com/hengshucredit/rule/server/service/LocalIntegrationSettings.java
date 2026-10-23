package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.util.LocalEnvironmentProperties;
import java.nio.file.Path;
import java.util.Properties;

/** 集成测试与服务启动使用同样的本机配置优先级，调用方仍必须检查回环地址与隔离表名。 */
final class LocalIntegrationSettings {
    private LocalIntegrationSettings() { }
    static Properties load(Path root) {
        Properties settings = new Properties();
        settings.putAll(LocalEnvironmentProperties.load(root, ".env.server"));
        System.getenv().forEach((key, value) -> { if (key.startsWith("MYSQL_")) settings.setProperty(key, value); });
        return settings;
    }
}
