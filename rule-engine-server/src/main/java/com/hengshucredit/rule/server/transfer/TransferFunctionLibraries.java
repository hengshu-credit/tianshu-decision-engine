package com.hengshucredit.rule.server.transfer;

import com.hengshucredit.rule.server.artifact.Sha256Digests;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarFile;

/** Only administrator-deployed extension JARs can be exported; imported bytes never enter the classpath. */
@Component
public class TransferFunctionLibraries {
    private final Path directory;
    @jakarta.annotation.Resource private org.springframework.context.ApplicationContext context;
    public TransferFunctionLibraries(@Value("${RULE_FUNCTION_EXTENSION_DIRECTORY:./extensions/functions}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    record Library(String digest, byte[] bytes) {}
    record Requirement(String className, String digest) {
        String path() { return "function-jars/" + digest + ".jar"; }
    }

    Library deployed(Map<String,Object> config, Map<Path,Library> cache) {
        boolean javaFunction = "JAVA".equals(config.get("implType"));
        if (!javaFunction && !"BEAN".equals(config.get("implType"))) return null;
        if (!(config.get(javaFunction ? "implClass" : "implBeanName") instanceof String name)) return null;
        try {
            Class<?> implementation;
            if (javaFunction) {
                implementation = Class.forName(name, false, getClass().getClassLoader());
            } else {
                if (context == null || !context.containsBean(name)) return null;
                implementation = context.getType(name, false);
            }
            if (implementation == null) return null;
            implementation = org.springframework.util.ClassUtils.getUserClass(implementation);
            var origin = implementation.getProtectionDomain().getCodeSource();
            if (origin == null || !"file".equals(origin.getLocation().getProtocol()) || !Files.isDirectory(directory)) return null;
            Path file = Path.of(origin.getLocation().toURI()).toRealPath();
            if (!Files.isRegularFile(file) || !directory.toRealPath().equals(file.getParent())
                    || !file.getFileName().toString().endsWith(".jar")) return null;
            try (var jar = new JarFile(file.toFile())) {
                if (jar.getJarEntry(implementation.getName().replace('.', '/') + ".class") == null) throw new IllegalArgumentException("扩展文件不包含函数类：" + name);
            }
            Library cached = cache.get(file);
            if (cached != null) return cached;
            byte[] bytes = Files.readAllBytes(file);
            Library library = new Library(Sha256Digests.bytes(bytes), bytes);
            cache.put(file, library);
            return library;
        } catch (ClassNotFoundException unavailable) {
            return null;
        } catch (IOException | URISyntaxException | LinkageError e) {
            throw new IllegalArgumentException("无法核对已部署的函数扩展：" + name,e);
        }
    }

    String issue(Requirement required, Map<String,Object> config, Map<Path,Library> cache) {
        Library installed = deployed(config, cache);
        if (installed == null || !installed.digest().equals(required.digest())) {
            return required.className() + " 需要部署包内 " + required.path() + " 并重启执行节点；现有扩展不会被覆盖";
        }
        return null;
    }
}
