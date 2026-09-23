package com.hengshucredit.rule.example.remote;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.junit.Assert.*;

public class OfflineBuildToolTest {
    @Test public void offlineCompilerPreservesPackageDirectoriesRequiredBySpringScanning() throws Exception {
        Path root = Files.createTempDirectory("offline-build-test-");
        Path source = root.resolve("example-src/src/main/java/demo/Customer.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package demo; public class Customer { public static String value() { return \"ok\"; } }");
        Files.createDirectories(root.resolve("example/lib"));
        Files.createDirectories(root.resolve("example-src/src/main/resources/static"));
        Files.writeString(root.resolve("example-src/src/main/resources/static/index.html"), "test-resource");
        Path tool = Path.of("distribution/tools/BuildExample.java").toAbsolutePath();
        Path log = root.resolve("build.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), tool.toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue("离线编译超时", process.waitFor(30, TimeUnit.SECONDS));
            assertEquals(Files.readString(log), 0, process.exitValue());
            Path jarPath = root.resolve("build/example.jar");
            try (JarFile jar = new JarFile(jarPath.toFile());
                 var loader = new java.net.URLClassLoader(new java.net.URL[]{jarPath.toUri().toURL()}, null)) {
                assertNotNull("组件扫描必须能枚举包目录", loader.getResource("demo/"));
                assertNotNull(jar.getJarEntry("static/"));
                assertEquals("test-resource", new String(jar.getInputStream(jar.getJarEntry("static/index.html")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                assertEquals("ok", loader.loadClass("demo.Customer").getMethod("value").invoke(null));
            }
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
