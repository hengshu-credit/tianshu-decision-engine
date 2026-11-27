package com.hengshucredit.rule.server.testdata;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Set;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.graalvm.polyglot.io.FileSystem;

/** 只将官方发行包中的 ES 模块映射给 JS，不访问宿主机文件或网络。 */
final class FakerModuleFiles implements FileSystem {
    private static final String DIST = "/META-INF/resources/_static/at/faker-js/faker/10.6.0/dist/";
    static final Path ROOT = Path.of("tianshu-faker-modules").toAbsolutePath().normalize();

    private String resource(Path path) throws IOException {
        Path full = toAbsolutePath(path);
        if (!full.startsWith(ROOT) || !full.toString().endsWith(".js")) throw new NoSuchFileException(path.toString());
        String name = DIST + ROOT.relativize(full).toString().replace('\\', '/');
        if (getClass().getResource(name) == null) throw new NoSuchFileException(name);
        return name;
    }

    @Override public Path parsePath(String path) { return Path.of(path); }
    @Override public Path parsePath(URI uri) { return Path.of(uri); }
    @Override public Path toAbsolutePath(Path path) { return ROOT.resolve(path).normalize(); }
    @Override public Path toRealPath(Path path, LinkOption... options) throws IOException {
        resource(path);
        return toAbsolutePath(path);
    }
    @Override public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... options) throws IOException {
        if (modes.contains(AccessMode.WRITE)) throw new AccessDeniedException(path.toString());
        resource(path);
    }
    @Override public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        if (!Set.of(StandardOpenOption.READ).containsAll(options)) throw new AccessDeniedException(path.toString());
        try (var stream = getClass().getResourceAsStream(resource(path))) {
            return new SeekableInMemoryByteChannel(stream.readAllBytes());
        }
    }
    @Override public Map<String,Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        resource(path);
        return Map.of("isRegularFile", true, "isDirectory", false, "isSymbolicLink", false, "isOther", false);
    }
    @Override public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException { throw new AccessDeniedException(dir.toString()); }
    @Override public void delete(Path path) throws IOException { throw new AccessDeniedException(path.toString()); }
    @Override public DirectoryStream<Path> newDirectoryStream(Path path, DirectoryStream.Filter<? super Path> filter) throws IOException {
        throw new AccessDeniedException(path.toString());
    }
}
