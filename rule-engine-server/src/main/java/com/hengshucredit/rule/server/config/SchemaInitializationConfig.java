package com.hengshucredit.rule.server.config;

import com.hengshucredit.rule.server.schema.MysqlSchemaUpgrade;
import com.hengshucredit.rule.server.service.SchemaSyncService;
import com.hengshucredit.rule.server.testdata.FakerMethods;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.sql.init.SqlDataSourceScriptDatabaseInitializer;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SqlInitializationProperties.class)
public class SchemaInitializationConfig {
    private static final Logger log = LoggerFactory.getLogger(SchemaInitializationConfig.class);

    @Bean
    public SqlDataSourceScriptDatabaseInitializer dataSourceScriptDatabaseInitializer(DataSource source, SqlInitializationProperties properties) {
        return new SqlDataSourceScriptDatabaseInitializer(source, properties) {
            private boolean schemaGroupProcessed;

            @Override protected void runScripts(Scripts scripts) {
                try (Connection connection = source.getConnection()) {
                    String catalog = connection.getCatalog();
                    if (catalog == null || catalog.isBlank()) throw new SQLException("未选择目标数据库");
                    String lock = "tianshu:schema:" + UUID.nameUUIDFromBytes(catalog.getBytes(StandardCharsets.UTF_8));
                    log.info("[SchemaUpgrade] 等待数据库结构锁 database={}", catalog);
                    try (var acquire = connection.prepareStatement("SELECT GET_LOCK(?,120)")) {
                        acquire.setString(1, lock);
                        try (var result = acquire.executeQuery()) {
                            if (!result.next() || result.getInt(1) != 1) {
                                log.error("[SchemaUpgrade] 升级失败 database={} 等待结构锁超时或未能取得锁，本实例未执行 DDL", catalog);
                                throw new SQLException("未能获取数据库结构初始化锁");
                            }
                        }
                    }
                    try {
                        boolean packaged = false;
                        var canonical = new ClassPathResource("sql/schema.sql");
                        for (var resource : scripts) {
                            if (resource.getURL().equals(canonical.getURL())) {
                                new MysqlSchemaUpgrade(connection).run(resource.getContentAsString(StandardCharsets.UTF_8));
                                // 在同一结构锁内补齐内置 Mock 规则，保留用户编辑过的配置。
                                var defaults = new ResourceDatabasePopulator(new ClassPathResource("sql/mock-defaults.sql"),
                                        new ClassPathResource("sql/migrations/2026-11-23-data-object-script-name.sql"));
                                defaults.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
                                defaults.populate(connection);
                                FakerMethods.install(connection);
                                packaged = true;
                            } else {
                                var populator = new ResourceDatabasePopulator();
                                populator.setContinueOnError(scripts.isContinueOnError());
                                populator.setSeparator(scripts.getSeparator());
                                if (scripts.getEncoding() != null) populator.setSqlScriptEncoding(scripts.getEncoding().name());
                                populator.addScript(resource);
                                customize(populator);
                                populator.populate(connection);
                            }
                        }
                        if (!schemaGroupProcessed) {
                            SchemaSyncService.finishInitialization(connection, packaged);
                            schemaGroupProcessed = true;
                        }
                        if (!connection.getAutoCommit()) connection.commit();
                        log.info("[SchemaUpgrade] 初始化完成 database={}", catalog);
                    } finally {
                        try (var release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                            release.setString(1, lock); release.execute();
                        }
                    }
                } catch (SQLException error) {
                    log.error("[SchemaUpgrade] 初始化未完成，停止启动 SQLState={} MySQL={}", error.getSQLState(), error.getErrorCode());
                    throw new IllegalStateException("数据库结构初始化失败", error);
                } catch (IOException | RuntimeException error) {
                    log.error("[SchemaUpgrade] 初始化未完成，停止启动 cause={}", error.getClass().getSimpleName());
                    throw new IllegalStateException("数据库结构初始化失败", error);
                }
            }
        };
    }
}
