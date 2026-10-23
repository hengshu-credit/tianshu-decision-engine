package com.hengshucredit.rule.core.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.*;

public class LocalEnvironmentPropertiesTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void onlyServiceFilesLoadWithoutLegacySettingsOrMixingClientAndServer() throws Exception {
        Path root = temporary.newFolder("repository").toPath();
        Path module = Files.createDirectory(root.resolve("server"));
        write(root.resolve(".env"), "MYSQL_HOST=legacy-parent\nLEGACY_ONLY=yes\n");
        write(module.resolve(".env"), "MYSQL_HOST=legacy-current\n");
        write(root.resolve(".env.server"), "MYSQL_HOST=server-parent\nMASTER_KEY=server-secret\n");
        write(module.resolve(".env.server"), "MYSQL_HOST=server-current\n");
        write(root.resolve(".env.client"), "MYSQL_HOST=wrong-client\nCLIENT_ONLY=private\n");

        Map<String, Object> values = LocalEnvironmentProperties.load(module, ".env.server");
        assertEquals("server-current", values.get("MYSQL_HOST"));
        assertEquals("server-secret", values.get("MASTER_KEY"));
        assertFalse(values.containsKey("LEGACY_ONLY"));
        assertFalse(values.containsKey("CLIENT_ONLY"));
        assertEquals("server-parent", LocalEnvironmentProperties.load(root, ".env.server").get("MYSQL_HOST"));
        assertFalse(LocalEnvironmentProperties.load(root, ".env.client").containsKey("MASTER_KEY"));
    }

    @Test
    public void obsoleteLocalFilesCannotOverrideServiceSettings() throws Exception {
        Path root = temporary.newFolder("local").toPath();
        Path module = Files.createDirectory(root.resolve("runtime"));
        write(root.resolve(".env.client"), "REDIS_HOST=production-redis\nSDK_ACCESS_TOKEN=production-token\n");
        write(root.resolve(".env.client.local"), "REDIS_HOST=127.0.0.1\nREDIS_PORT=16379\n");
        Map<String, Object> values = LocalEnvironmentProperties.load(module, ".env.client");
        assertEquals("production-redis", values.get("REDIS_HOST"));
        assertFalse(values.containsKey("REDIS_PORT"));
        assertEquals("production-token", values.get("SDK_ACCESS_TOKEN"));
    }

    @Test
    public void dotenvQuotesPreserveBcryptDollarSignsHashesAndBackslashes() throws Exception {
        Path root = temporary.newFolder("quoted").toPath();
        write(root.resolve(".env.server"), "\uFEFF# settings\n"
                + "CONSOLE_PASSWORD='$2b$10$example/hash#value' # comment\n"
                + "MYSQL_PASSWORD=\"secret#with=characters\\path\"\n"
                + "export MYSQL_HOST = localhost # local\nEMPTY=\n");
        Map<String, Object> values = LocalEnvironmentProperties.load(root, ".env.server");
        assertEquals("$2b$10$example/hash#value", values.get("CONSOLE_PASSWORD"));
        assertEquals("secret#with=characters\\path", values.get("MYSQL_PASSWORD"));
        assertEquals("localhost", values.get("MYSQL_HOST"));
        assertEquals("", values.get("EMPTY"));
    }

    @Test
    public void missingFilesAreOptionalAndInvalidValuesAreNeverPrinted() throws Exception {
        Path root = temporary.newFolder("missing").toPath();
        assertTrue(LocalEnvironmentProperties.load(root, ".env.client").isEmpty());
        write(root.resolve(".env.client"), "PASSWORD='unclosed-secret\n");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> LocalEnvironmentProperties.load(root, ".env.client"));
        assertTrue(error.getMessage().contains(".env.client:1"));
        assertFalse(error.getMessage().contains("unclosed-secret"));
    }

    private void write(Path file, String content) throws Exception {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
