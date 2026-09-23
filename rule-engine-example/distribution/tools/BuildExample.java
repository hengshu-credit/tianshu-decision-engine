import javax.tools.ToolProvider;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Collectors;

/** JDK 17 单文件工具：只用随包依赖编译示例，不调用 Maven，不访问网络。 */
class BuildExample {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path sources = root.resolve("example-src/src/main/java");
        Path libs = root.resolve("example/lib");
        if (!Files.isDirectory(sources) || !Files.isDirectory(libs)) throw new IllegalStateException("请在解压目录运行此工具");
        Path build = root.resolve("build");
        Files.createDirectories(build);
        // 每次编译使用新的临时目录，不复用可能含已删除源文件旧 class 的目录。
        Path classes = Files.createTempDirectory(build, "classes-");
        String classpath;
        try (var paths = Files.list(libs)) {
            classpath = paths.filter(p -> p.toString().endsWith(".jar")).sorted()
                    .map(Path::toString).collect(Collectors.joining(File.pathSeparator));
        }
        List<String> options = new ArrayList<>(List.of("--release", "17", "-encoding", "UTF-8", "-parameters", "-proc:none", "-cp", classpath, "-d", classes.toString()));
        try (var paths = Files.walk(sources)) { paths.filter(p -> p.toString().endsWith(".java")).sorted().map(Path::toString).forEach(options::add); }
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("需要完整 JDK 17，不是 JRE");
        if (compiler.run(null, null, null, options.toArray(String[]::new)) != 0) throw new IllegalStateException("示例编译失败");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (var jar = new JarOutputStream(Files.newOutputStream(build.resolve("example.jar")), manifest)) {
            Set<String> entries = new HashSet<>(List.of("META-INF/", "META-INF/MANIFEST.MF"));
            add(jar, classes, entries);
            add(jar, root.resolve("example-src/src/main/resources"), entries);
        }
        System.out.println("Built build/example.jar without Maven or network. Run start.ps1 -Rebuilt or sh start.sh --rebuilt");
    }
    static void add(JarOutputStream jar, Path directory, Set<String> entries) throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path file : paths.filter(p -> !p.equals(directory)).sorted().toList()) {
                boolean isDirectory = Files.isDirectory(file);
                String name = directory.relativize(file).toString().replace('\\', '/') + (isDirectory ? "/" : "");
                if (!entries.add(name)) continue;
                // Spring 组件扫描依赖包目录条目；只有 .class 条目的 JAR 会漏掉控制器。
                jar.putNextEntry(new JarEntry(name));
                if (!isDirectory) Files.copy(file, jar);
                jar.closeEntry();
            }
        }
    }
}
