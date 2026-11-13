package com.hengshucredit.rule.server.config;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.sql.init.SqlDataSourceScriptDatabaseInitializer;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SqlInitializationProperties.class)
public class SchemaInitializationConfig {
    @Bean
    SqlDataSourceScriptDatabaseInitializer dataSourceScriptDatabaseInitializer(DataSource source, SqlInitializationProperties properties) {
        return new SqlDataSourceScriptDatabaseInitializer(source, properties) {
            @Override protected void runScripts(Scripts scripts) {
                // MySQL's IF NOT EXISTS does not serialize concurrent metadata/FK locks taken by different replicas.
                try (Connection connection = source.getConnection()) {
                    String lock = "tianshu:schema:" + java.util.UUID.nameUUIDFromBytes(
                            connection.getCatalog().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    try (var acquire = connection.prepareStatement("SELECT GET_LOCK(?,120)")) {
                        acquire.setString(1, lock);
                        try (var result = acquire.executeQuery()) {
                            if (!result.next() || result.getInt(1) != 1) throw new SQLException("获取数据库结构初始化锁超时");
                        }
                    }
                    try {
                        var populator = new ResourceDatabasePopulator();
                        populator.setContinueOnError(scripts.isContinueOnError());
                        populator.setSeparator(scripts.getSeparator());
                        if (scripts.getEncoding() != null) populator.setSqlScriptEncoding(scripts.getEncoding().name());
                        scripts.forEach(populator::addScript);
                        customize(populator);
                        populator.populate(connection);
                        if (!connection.getAutoCommit()) connection.commit();
                    } finally {
                        try (var release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                            release.setString(1, lock); release.execute();
                        }
                    }
                } catch (SQLException error) {
                    throw new IllegalStateException("数据库结构初始化失败", error);
                }
            }
        };
    }
}
