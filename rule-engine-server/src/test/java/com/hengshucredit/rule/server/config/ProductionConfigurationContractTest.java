package com.hengshucredit.rule.server.config;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    public void containerConfigurationRequiresExternalPasswords() throws Exception {
        Path root = repositoryRoot();
        String rootCompose = read(root.resolve("docker-compose.yaml"));
        String mysqlCompose = read(root.resolve("rule-engine-mysql/docker-compose.yaml"));
        String redisCompose = read(root.resolve("rule-engine-redis/docker-compose.yml"));
        String redisConfig = read(root.resolve("rule-engine-redis/redis.conf"));

        Assert.assertFalse(rootCompose.contains(LEGACY_SHARED_PASSWORD));
        Assert.assertFalse(mysqlCompose.contains(LEGACY_SHARED_PASSWORD));
        Assert.assertFalse(redisCompose.contains(LEGACY_SHARED_PASSWORD));
        Assert.assertFalse(redisConfig.contains(LEGACY_SHARED_PASSWORD));
        Assert.assertTrue(rootCompose.contains("${MYSQL_ROOT_PASSWORD:?"));
        Assert.assertTrue(rootCompose.contains("MYSQL_USER: \"${MYSQL_USERNAME:?"));
        Assert.assertTrue(rootCompose.contains("MYSQL_PASSWORD: \"${MYSQL_PASSWORD:?"));
        Assert.assertTrue(mysqlCompose.contains("MYSQL_USER=${MYSQL_USERNAME:?"));
        Assert.assertTrue(mysqlCompose.contains("MYSQL_PASSWORD=${MYSQL_PASSWORD:?"));
        Assert.assertTrue(rootCompose.contains("${REDIS_PASSWORD:?"));
    }

    private static Path repositoryRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        return Files.isDirectory(cwd.resolve("rule-engine-server")) ? cwd : cwd.getParent();
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
