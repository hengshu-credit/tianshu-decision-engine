package com.hengshucredit.rule.server.config;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ProductionConfigurationContractTest {

    private static final String LEGACY_SHARED_PASSWORD = "1qaz@WSX";
    private static final String LEGACY_MASTER_KEY = "L?g48_wA5HJhqAFzehpmsfm~)Ja#me@#";

    @Test
    public void applicationConfigurationDoesNotProvideSharedCredentialDefaults() throws Exception {
        String application = read(repositoryRoot().resolve(
                "rule-engine-server/src/main/resources/application.yml"));

        Assert.assertFalse(application.contains(LEGACY_SHARED_PASSWORD));
        Assert.assertFalse(application.contains(LEGACY_MASTER_KEY));
        Assert.assertTrue(application.contains("${MYSQL_PASSWORD:}"));
        Assert.assertTrue(application.contains("${RULE_AUTH_MASTER_KEY:}"));
        Assert.assertTrue(application.contains("${CONSOLE_PASSWORD:}"));
    }

    @Test
    public void applicationConfigurationLoadsIgnoredLocalEnvFiles() throws Exception {
        Path root = repositoryRoot();
        String application = read(root.resolve(
                "rule-engine-server/src/main/resources/application.yml"));
        String gitignore = read(root.resolve(".gitignore"));

        Assert.assertTrue(application.contains("optional:file:.env[.properties]"));
        Assert.assertTrue(application.contains("optional:file:../.env[.properties]"));
        Assert.assertTrue(gitignore.matches("(?s).*(^|\\R)/\\.env(\\R|$).*"));
    }

    @Test
    public void serverInitializesPackagedSchemaByDefaultWithoutImportingData() {
        Path resources = repositoryRoot().resolve("rule-engine-server/src/main/resources");
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(resources.resolve("application.yml")));
        Properties properties = yaml.getObject();
        MockEnvironment environment = new MockEnvironment();
        String mode = properties.getProperty("spring.sql.init.mode");

        Assert.assertEquals("always", environment.resolveRequiredPlaceholders(mode));
        Assert.assertEquals("classpath:sql/schema.sql", properties.getProperty("spring.sql.init.schema-locations"));
        Assert.assertEquals("false", environment.resolveRequiredPlaceholders(
                properties.getProperty("spring.sql.init.continue-on-error")));
        Assert.assertNull(properties.getProperty("spring.sql.init.data-locations"));
        Assert.assertFalse(Files.exists(resources.resolve("data.sql")));
        Assert.assertFalse(Files.exists(resources.resolve("sql/data-tianshu-example.sql")));
        Assert.assertTrue(Files.isRegularFile(repositoryRoot().resolve(
                "docker/rule-engine-mysql/data-tianshu-example.sql")));
        environment.setProperty("SPRING_SQL_INIT_MODE", "never");
        Assert.assertEquals("never", environment.resolveRequiredPlaceholders(mode));
    }

    @Test
    public void containerConfigurationRequiresExternalPasswords() throws Exception {
        Path root = repositoryRoot();
        String appCompose = read(root.resolve("docker/docker-compose.yml"));
        String fullCompose = read(root.resolve("docker/docker-compose.full.yml"));
        String mysqlCompose = read(root.resolve("docker/docker-compose.mysql.yml"));
        String redisCompose = read(root.resolve("docker/docker-compose.redis.yml"));

        Assert.assertFalse(appCompose.contains(LEGACY_SHARED_PASSWORD));
        Assert.assertFalse(fullCompose.contains(LEGACY_SHARED_PASSWORD));
        Assert.assertFalse(mysqlCompose.contains(LEGACY_SHARED_PASSWORD));
        Assert.assertFalse(redisCompose.contains(LEGACY_SHARED_PASSWORD));
        Assert.assertFalse(appCompose.contains("mysql-init:"));
        Assert.assertTrue(appCompose.contains("MYSQL_SERVICE_HOST:-host.docker.internal"));
        Assert.assertTrue(appCompose.contains("REDIS_SERVICE_HOST:-host.docker.internal"));
        Assert.assertTrue(appCompose.contains("runtime-http:"));
        Assert.assertTrue(appCompose.contains("runtime-sdk:"));
        Assert.assertTrue(appCompose.contains("./tianshu-decision-engine-runtime"));
        Assert.assertTrue(fullCompose.contains("mysql:"));
        Assert.assertTrue(fullCompose.contains("redis:"));
        Assert.assertFalse(fullCompose.contains("mysql-init:"));
        Assert.assertTrue(fullCompose.contains("condition: service_healthy"));
        Assert.assertTrue(fullCompose.contains("  http:"));
        Assert.assertTrue(fullCompose.contains("  sdk:"));
        Assert.assertTrue(mysqlCompose.contains("MYSQL_ROOT_PASSWORD: \"${MYSQL_ROOT_PASSWORD:?"));
        Assert.assertTrue(mysqlCompose.contains("MYSQL_USER: \"${MYSQL_USERNAME:?"));
        Assert.assertTrue(mysqlCompose.contains("MYSQL_PASSWORD: \"${MYSQL_PASSWORD:?"));
        Assert.assertTrue(redisCompose.contains("${REDIS_PASSWORD:?"));
        Assert.assertTrue(appCompose.contains("${REDIS_PASSWORD:?"));
    }

    @Test
    public void infrastructureBindsDataUnderDockerWithoutDuplicateComposeFiles() throws Exception {
        Path root = repositoryRoot();
        String mysql = read(root.resolve("docker/docker-compose.mysql.yml"));
        String redis = read(root.resolve("docker/docker-compose.redis.yml"));
        Assert.assertTrue(mysql.contains("./rule-engine-mysql/data:/var/lib/mysql"));
        Assert.assertTrue(mysql.contains("./rule-engine-mysql/logs:/var/log/mysql"));
        Assert.assertTrue(mysql.contains("./rule-engine-mysql/conf.d:/etc/mysql/conf.d:ro"));
        Assert.assertTrue(redis.contains("./rule-engine-redis/data:/data"));
        Assert.assertTrue(redis.contains("./rule-engine-redis/redis.conf:/usr/local/etc/redis/redis.conf:ro"));
        Assert.assertFalse(mysql.contains("mysql-data:/var/lib/mysql"));
        Assert.assertFalse(redis.contains("redis-data:/data"));
        Assert.assertTrue(Files.isRegularFile(root.resolve("docker/rule-engine-mysql/conf.d/itlubber.cnf")));
        Assert.assertTrue(Files.isRegularFile(root.resolve("docker/rule-engine-redis/redis.conf")));
        Assert.assertFalse(Files.exists(root.resolve("docker/rule-engine-mysql/docker-compose.yaml")));
        Assert.assertFalse(Files.exists(root.resolve("docker/rule-engine-redis/docker-compose.yml")));
    }

    private static Path repositoryRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        return Files.isDirectory(cwd.resolve("rule-engine-server")) ? cwd : cwd.getParent();
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
