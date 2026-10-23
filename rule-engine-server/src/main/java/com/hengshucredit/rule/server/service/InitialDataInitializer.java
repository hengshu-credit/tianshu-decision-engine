package com.hengshucredit.rule.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Runs optional, idempotent startup data scripts after the schema has been
 * initialized.  The scripts must only insert missing rows; existing rows are
 * never updated or deleted by this component.
 */
@Service
@DependsOn("schemaSyncService")
public class InitialDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(InitialDataInitializer.class);

    @Autowired
    private InitialDataProperties properties;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void initialize() {
        if (!properties.isEnabled()) {
            return;
        }
        List<String> locations = properties.getLocations();
        if (locations == null || locations.isEmpty()) {
            log.info("[InitialData] 已启用，但未配置初始化脚本，跳过");
            return;
        }
        int executed = 0;
        try (Connection connection = dataSource.getConnection()) {
            String lockName = "tianshu:initial-data:" + String.valueOf(connection.getCatalog());
            if (!acquireLock(connection, lockName)) {
                throw new IllegalStateException("等待其他 server 执行初始配置超时");
            }
            try {
                for (String location : locations) {
                    if (location == null || location.isBlank()) {
                        continue;
                    }
                    Resource resource = resourceLoader.getResource(location.trim());
                    if (!resource.exists()) {
                        throw new IllegalStateException("初始化脚本不存在: " + location);
                    }
                    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                    populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
                    populator.addScript(resource);
                    populator.populate(connection);
                    executed++;
                }
            } finally {
                releaseLock(connection, lockName);
            }
            log.info("[InitialData] 启动初始化脚本执行完成: scripts={}", executed);
        } catch (Exception e) {
            throw new IllegalStateException("启动初始化数据失败，已停止服务启动", e);
        }
    }

    private boolean acquireLock(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 60)")) {
            statement.setString(1, lockName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && Integer.valueOf(1).equals(result.getInt(1));
            }
        }
    }

    private void releaseLock(Connection connection, String lockName) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, lockName);
            statement.executeQuery();
        } catch (Exception e) {
            log.warn("[InitialData] 释放启动初始化锁失败: {}", e.getMessage());
        }
    }
}
