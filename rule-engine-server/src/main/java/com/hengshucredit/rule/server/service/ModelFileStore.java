package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.artifact.DecisionArtifactPackage;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.server.upload.UploadSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** 不可变模型原件及冻结制品。数据库只记录相对标识，传输包携带真实字节。 */
@Component
public final class ModelFileStore {
    private static final String PREFIX = "ts-file-v1:";
    private static final Pattern KEY = Pattern.compile("(models/[0-9]+|packages)/[0-9a-f]{2}/[\\p{L}\\p{N}_.-]+--[0-9a-f]{64}\\.(onnx|pmml|zip)");
    private final Path root;
    private final MysqlModelBlobStore shared;
    private final UploadSecurity security;
    private final long fileLimit;
    private final long cacheLimit;
    private final Map<String, byte[]> cache = new LinkedHashMap<>(16, 0.75f, true);
    private long cachedBytes;
    private final VerifiedFileCache largeFiles = new VerifiedFileCache();
    @Value("${rule-engine.model.local-file-cache-bytes:1073741824}")
    private long localFileCacheBytes;

    @jakarta.annotation.PreDestroy
    void closeLocalCache() { largeFiles.close(); }

    public ModelFileStore(String directory, long fileLimit, long cacheLimit, UploadSecurity security) {
        this(directory, fileLimit, cacheLimit, security, "FILESYSTEM", null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ModelFileStore(@Value("${rule-engine.model.storage-directory:./data/model-files}") String directory,
                          @Value("${rule-engine.model.max-file-size-bytes:268435456}") long fileLimit,
                          @Value("${rule-engine.model.file-cache-bytes:134217728}") long cacheLimit,
                          UploadSecurity security,
                          @Value("${rule-engine.model.storage-backend:MYSQL}") String backend,
                          MysqlModelBlobStore database) {
        this.shared = switch (backend.toUpperCase(Locale.ROOT)) {
            case "MYSQL" -> java.util.Objects.requireNonNull(database);
            case "FILESYSTEM" -> null;
            default -> throw new IllegalArgumentException("未知模型存储后端：" + backend);
        };
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.fileLimit = fileLimit;
        this.cacheLimit = cacheLimit;
        this.security = security;
    }

    public static boolean isReference(String value) { return value != null && value.startsWith(PREFIX); }

    public static byte[] bytes(ModelFileStore store, Long owner, String filename, String format, String content, String digest) {
        if (store == null) {
            if (isReference(content)) throw new IllegalStateException("未配置模型文件仓库");
            return legacyBytes(content);
        }
        String name = filename == null || filename.isBlank() ? "model." + ("PMML".equalsIgnoreCase(format) ? "pmml" : "onnx") : filename;
        if (isReference(content) && format != null && !content.toLowerCase(Locale.ROOT).endsWith("." + format.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("模型文件与版本格式不一致");
        }
        return store.model(owner, name, content, digest);
    }

    public static String contentDigest(String content) {
        if (!isReference(content)) return Sha256Digests.bytes(legacyBytes(content));
        if (!KEY.matcher(content.substring(PREFIX.length())).matches()) throw new IllegalArgumentException("模型文件标识无效");
        return content.substring(content.lastIndexOf("--") + 2, content.lastIndexOf('.'));
    }

    private static byte[] legacyBytes(String content) {
        if (content == null) throw new IllegalArgumentException("模型内容为空");
        try { return Base64.getDecoder().decode(content); }
        catch (IllegalArgumentException invalidBase64) {
            if (content.stripLeading().startsWith("<")) return content.getBytes(StandardCharsets.UTF_8);
            throw invalidBase64;
        }
    }

    public static byte[] snapshotBytes(ModelFileStore store, Map<String, Object> snapshot) {
        if (snapshot.get("packageFile") instanceof String file) {
            if (store == null) throw new IllegalStateException("未配置版本制品仓库");
            return store.packageBytes(file, String.valueOf(snapshot.get("packageDigest")));
        }
        return Base64.getDecoder().decode(String.valueOf(snapshot.get("packageContent")));
    }

    /** 历史快照外移后仍按原序列化内容验证原配置摘要，保持已发布制品的归因。 */
    public static Map<String,Object> checkedSnapshot(ModelFileStore store, String json, String expected) {
        if (json == null || expected == null) throw new IllegalArgumentException("缺少模型版本快照");
        // 旧快照的 packageContent 可能超过普通 JSON 字符串读取上限。
        Map<String,Object> snapshot = com.hengshucredit.rule.server.artifact.CanonicalJson.readModelSnapshot(json);
        if (Sha256Digests.text(json).equals(expected)) return snapshot;
        if (store != null && expected.equals(snapshot.get("legacyInlineDigest")) && snapshot.containsKey("packageFile")) {
            var original = new LinkedHashMap<>(snapshot);
            original.remove("legacyInlineDigest");
            original.remove("packageFile");
            original.put("packageContent", Base64.getEncoder().encodeToString(snapshotBytes(store, snapshot)));
            if (expected.equals(Sha256Digests.text(com.hengshucredit.rule.server.artifact.CanonicalJson.write(original)))) return snapshot;
        }
        throw new IllegalArgumentException("模型版本执行快照摘要校验失败");
    }

    private static String scope(Long projectId) { return "models/" + (projectId == null ? 0L : projectId); }

    public String accept(Long projectId, String filename, byte[] bytes) {
        return saveOriginal(projectId, filename, bytes, false);
    }

    /** 冻结版本或数据库历史记录可复用已扫描的同摘要文件；外部新上传始终重新扫描。 */
    public String fromSnapshot(Long projectId, String filename, byte[] bytes) {
        return saveOriginal(projectId, filename, bytes, true);
    }

    /** 制品解码已验证原件摘要。目标安装已有相同原件时只校验存储，不展开第二份模型权重。 */
    public String fromVerifiedSnapshot(Long projectId, String filename, int length, String digest,
                                       java.util.function.Supplier<byte[]> source) {
        UploadSecurity.filename(filename);
        if (length <= 0 || length > fileLimit || digest == null || !digest.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("冻结模型文件大小或摘要无效");
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".onnx") && !lower.endsWith(".pmml")) throw new IllegalArgumentException("仅支持 ONNX 或 PMML 原始模型文件");
        String reference = key(scope(projectId), filename, digest);
        if (shared != null ? shared.contains(reference) : Files.exists(path(reference), LinkOption.NOFOLLOW_LINKS)) {
            if (largeFiles.verifies(reference, digest, length)) return reference;
            if (verifiedRead(reference, digest).length != length) throw new IllegalArgumentException("冻结模型大小不匹配");
            return reference;
        }
        byte[] original = source.get();
        if (original.length != length) throw new IllegalArgumentException("冻结模型大小不匹配");
        checkDigest(original, digest);
        return accept(projectId, filename, original);
    }

    private String saveOriginal(Long projectId, String filename, byte[] bytes, boolean trustedSnapshot) {
        UploadSecurity.filename(filename);
        if (bytes.length == 0 || bytes.length > fileLimit) throw new IllegalArgumentException("模型大小超过限制或为空");
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".onnx") && !lower.endsWith(".pmml")) throw new IllegalArgumentException("仅支持 ONNX 或 PMML 原始模型文件");
        String digest = Sha256Digests.bytes(bytes);
        String reference = key(scope(projectId), filename, digest);
        if (trustedSnapshot && (shared != null ? shared.contains(reference) : Files.exists(path(reference), LinkOption.NOFOLLOW_LINKS))) {
            verifiedRead(reference, digest);
            return reference;
        }
        screen(filename, bytes);
        install(reference, bytes);
        return reference;
    }

    /** 用小样本确认当前节点的 ClamD 能处理 INSTREAM，导入时仍独立扫描每个原件。 */
    public String importScanIssue() {
        var probe = security.scan("tianshu-upload-readiness".getBytes(StandardCharsets.US_ASCII));
        if ("CLEAN".equals(probe.status())) return null;
        return "模型扫描服务未就绪：" + probe.message() + "。请核对 ClamD、病毒库与服务端连接后重新预检。";
    }

    private void screen(String filename, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > fileLimit) throw new IllegalArgumentException("模型文件大小无效");
        var result = security.scan(bytes);
        if (!"CLEAN".equals(result.status())) {
            throw new IllegalArgumentException("模型安全检测 " + result.status() + "：" + result.message() + "。模型未入库");
        }
        if (filename.toLowerCase(Locale.ROOT).endsWith(".pmml")) requireStandalonePmml(bytes);
        else com.hengshucredit.rule.server.upload.StandaloneOnnx.require(bytes);
    }

    /** 外来迁移包只能携带原件，本机存储定位符不能从迁移包导入。 */
    public void ingestConfiguration(Map<String, Object> cfg, Long owner) {
        cfg.remove("_modelContentVersionId");
        if (!(cfg.get("modelContent") instanceof String data) || isReference(data)) {
            throw new IllegalArgumentException("模型迁移包缺少可验证的原始文件");
        }
        if (data.length() > 4L * ((fileLimit + 2) / 3)) throw new IllegalArgumentException("模型迁移原件超过上传大小上限");
        ingestConfiguration(cfg, owner, legacyBytes(data));
    }

    public void ingestConfiguration(Map<String,Object> cfg, Long owner, byte[] bytes) {
        if (bytes.length > fileLimit) throw new IllegalArgumentException("模型迁移原件超过上传大小上限");
        checkDigest(bytes, (String) cfg.get("modelDigest"));
        String ref = accept(owner, (String) cfg.get("modelFileName"), bytes);
        cfg.put("modelContent", ref);
        cfg.put("modelDigest", Sha256Digests.bytes(bytes));
        cfg.put("modelFileSize", (long) bytes.length);
    }

    public void screenEmbeddedModels(DecisionArtifactPackage pack) {
        for (var c : pack.getComponents().values()) {
            if ("MODEL".equals(c.getMetadata().get("resourceType"))) {
                Object declared = c.getMetadata().get("modelFormat");
                Object nested = c.getMetadata().get("model") instanceof Map<?,?> m ? m.get("modelFormat") : null;
                String format = String.valueOf(nested == null ? declared : nested);
                if (declared != null && !format.equalsIgnoreCase(declared.toString())) throw new IllegalArgumentException("制品模型格式声明冲突");
                if (!format.equalsIgnoreCase("PMML") && !format.equalsIgnoreCase("ONNX")) {
                    throw new IllegalArgumentException("制品包含未知格式的模型");
                }
                screen("embedded." + format, c.getContent());
            }
        }
    }

    public byte[] artifactContent(DecisionArtifactPackage pack, byte[] bytes) {
        if (!hasStoredModels(pack)) return bytes;
        if (shared == null) return savePackage(bytes).getBytes(StandardCharsets.UTF_8);
        Map<String, String> originals = originalDigests(pack);
        String digest = Sha256Digests.bytes(bytes);
        String ref = key("packages", "snapshot.zip", digest);
        shared.put(ref, digest, bytes, true, originals);
        if (cacheLimit > 0 && bytes.length > cacheLimit / 4) largeFiles.put(ref, bytes, localFileCacheBytes);
        return ref.getBytes(StandardCharsets.UTF_8);
    }

    private boolean hasStoredModels(DecisionArtifactPackage pack) {
        return pack.getComponents().values().stream().anyMatch(c -> "MODEL".equals(c.getMetadata().get("resourceType"))
                || c.getPath().startsWith("resources/versions/MODEL-")
                || c.getPath().startsWith("resources/versions/MODEL_OUTPUT-"));
    }

    public boolean storesSharedArchive(DecisionArtifactPackage pack) { return shared != null && hasStoredModels(pack); }

    public byte[] storeArchive(DecisionArtifactPackage pack, Path archive, String digest, int length) {
        String ref = key("packages", "snapshot.zip", digest);
        shared.putArchive(ref, digest, archive, length, originalDigests(pack));
        return ref.getBytes(StandardCharsets.UTF_8);
    }

    private Map<String, String> originalDigests(DecisionArtifactPackage pack) {
        Map<String, String> originals = new LinkedHashMap<>();
        for (var component : pack.getComponents().values()) {
            if ("MODEL".equals(component.getMetadata().get("resourceType"))) originals.put(component.getPath(), component.digest());
        }
        return originals;
    }

    public byte[] model(Long projectId, String fileName, String content, String expectedDigest) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("模型文件内容为空");
        if (!isReference(content)) {
            byte[] legacy = legacyBytes(content);
            checkDigest(legacy, expectedDigest);
            fromSnapshot(projectId, fileName, legacy);
            return legacy;
        }
        if (!content.startsWith(PREFIX + scope(projectId) + "/")) throw new IllegalArgumentException("模型文件不属于此作用域");
        return verifiedRead(content, expectedDigest);
    }

    public String relocate(Long owner, String filename, String content, String digest) {
        if (isReference(content)) {
            model(owner, filename, content, digest);
            return content;
        }
        byte[] original = legacyBytes(content);
        checkDigest(original, digest);
        return fromSnapshot(owner, filename, original);
    }

    public String savePackage(byte[] bytes) {
        String reference = key("packages", "snapshot.zip", Sha256Digests.bytes(bytes));
        install(reference, bytes);
        return reference;
    }

    public byte[] packageBytes(String value, String digest) {
        if (!isReference(value)) return Base64.getDecoder().decode(value);
        if (!value.startsWith(PREFIX + "packages/")) throw new IllegalArgumentException("模型制品引用类型无效");
        return verifiedRead(value, digest);
    }

    public byte[] resolveArtifact(byte[] stored, String digest) {
        if (stored == null || stored.length > 512 || stored.length < PREFIX.length() || stored[0] != 't') return stored;
        String ref = new String(stored, StandardCharsets.UTF_8);
        return isReference(ref) ? packageBytes(ref, digest) : stored;
    }

    public static DecisionArtifact hydrated(ModelFileStore store, DecisionArtifact artifact) {
        if (store != null && artifact != null) artifact.setPackageContent(store.resolveArtifact(artifact.getPackageContent(), artifact.getPackageDigest()));
        return artifact;
    }

    private static String key(String directory, String filename, String digest) {
        int dot = filename.lastIndexOf('.');
        String stem = filename.substring(0, dot).replaceAll("[^\\p{L}\\p{N}_.-]", "_");
        stem = stem.codePoints().limit(42).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        if (stem.isBlank() || stem.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?$")) stem = "_" + stem;
        return PREFIX + directory + "/" + digest.substring(0, 2) + "/" + stem + "--" + digest + filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private Path path(String reference) {
        String key = reference.substring(PREFIX.length());
        if (!KEY.matcher(key).matches()) throw new IllegalArgumentException("模型文件标识无效");
        Path target = root.resolve(key).normalize();
        for (Path p = target; !p.equals(root); p = p.getParent()) {
            if (Files.isSymbolicLink(p)) throw new IllegalStateException("模型存储目录包含符号链接");
        }
        return target;
    }

    private void install(String reference, byte[] bytes) {
        if (shared != null) {
            shared.put(reference, contentDigest(reference), bytes, reference.startsWith(PREFIX + "packages/"));
            return;
        }
        Path dest = path(reference);
        if (Files.isRegularFile(dest, LinkOption.NOFOLLOW_LINKS)) {
            verifiedRead(reference, null);
            return;
        }
        try {
            Files.createDirectories(dest.getParent());
            Path temp = Files.createTempFile(dest.getParent(), ".upload-", ".tmp");
            try {
                try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                try {
                    Files.move(temp, dest, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    throw new IOException("模型存储卷必须支持同目录原子重命名", unsupported);
                } catch (IOException raced) {
                    // Windows may return AccessDeniedException when another writer already installed the same immutable file.
                    if (!Files.isRegularFile(dest, LinkOption.NOFOLLOW_LINKS)) throw raced;
                    verifiedRead(reference, null);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) { throw new IllegalStateException("写入模型存储失败", e); }
    }

    private byte[] verifiedRead(String ref, String expected) {
        String digest = contentDigest(ref);
        checkExpected(expected, digest);
        synchronized (cache) {
            byte[] found = cache.get(ref);
            if (found != null) return found.clone();
        }
        if (shared != null && cacheLimit > 0 && localFileCacheBytes > 0) {
            byte[] local = largeFiles.read(ref, digest);
            if (local != null) return local;
        }
        try {
            long limit = ref.startsWith(PREFIX + "models/") ? fileLimit : 512L * 1024 * 1024;
            byte[] bytes;
            if (shared != null) {
                if (!shared.contains(ref)) importLegacyFile(ref, digest, limit);
                bytes = shared.read(ref, digest, limit);
            } else {
                bytes = readFile(ref, limit);
            }
            checkDigest(bytes, digest);
            if (shared != null && cacheLimit > 0 && bytes.length > cacheLimit / 4 && localFileCacheBytes >= bytes.length)
                largeFiles.put(ref, bytes, localFileCacheBytes);
            else if (bytes.length <= cacheLimit) synchronized (cache) {
                if (cache.put(ref, bytes.clone()) == null) cachedBytes += bytes.length;
                while (cachedBytes > cacheLimit) {
                    var iterator = cache.entrySet().iterator();
                    cachedBytes -= iterator.next().getValue().length;
                    iterator.remove();
                }
            }
            return bytes;
        } catch (IOException e) { throw new IllegalStateException("模型文件缺失或无法读取：" + ref, e); }
    }

    private byte[] readFile(String ref, long limit) throws IOException {
        Path source = path(ref);
        if (Files.size(source) > limit) throw new IllegalArgumentException("文件超出读取上限");
        return Files.readAllBytes(source);
    }

    /** Explicit migration reads through to storage even if a JVM has already warmed its cache. */
    public void ensureShared(String ref, String expected) {
        if (!isReference(ref)) throw new IllegalArgumentException("缺少不可变文件引用");
        String digest = contentDigest(ref);
        checkExpected(expected, digest);
        long limit = ref.startsWith(PREFIX + "packages/") ? 512L * 1024 * 1024 : fileLimit;
        try {
            if (shared == null) {
                checkDigest(readFile(ref, limit),digest);
            } else {
                if (!shared.contains(ref)) importLegacyFile(ref,digest,limit);
                shared.read(ref,digest,limit);
            }
        } catch (IOException e) { throw new IllegalStateException("迁移时无法读取旧模型卷：" + ref, e); }
    }

    private static void checkExpected(String expected, String digest) {
        if (expected != null && !expected.isBlank() && !digest.equalsIgnoreCase(expected))
            throw new IllegalArgumentException("模型引用与保存摘要不一致");
    }

    private void importLegacyFile(String ref, String digest, long limit) throws IOException {
        byte[] bytes = readFile(ref, limit);
        checkDigest(bytes, digest);
        boolean archive = ref.startsWith(PREFIX + "packages/");
        if (archive) {
            var decoded = new com.hengshucredit.rule.server.artifact.DecisionArtifactPackageCodec().decode(bytes);
            screenEmbeddedModels(decoded.getArtifactPackage());
        } else {
            screen(path(ref).getFileName().toString(), bytes);
        }
        shared.put(ref,digest,bytes,archive);
    }

    private static void checkDigest(byte[] bytes, String digest) {
        if (digest != null && !digest.isBlank() && !digest.equalsIgnoreCase(Sha256Digests.bytes(bytes))) {
            throw new IllegalArgumentException("模型文件内容摘要校验失败");
        }
    }

    private static void requireStandalonePmml(byte[] bytes) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try {
            var reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
            try {
                boolean rootSeen = false;
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD) throw new IllegalArgumentException("PMML 禁止文档类型声明");
                    if (event == XMLStreamConstants.START_ELEMENT && !rootSeen) {
                        if (!"PMML".equals(reader.getLocalName())) throw new IllegalArgumentException("模型根元素必须为 PMML");
                        rootSeen = true;
                    }
                }
                if (!rootSeen) throw new IllegalArgumentException("PMML 文档为空");
            } finally { reader.close(); }
        } catch (javax.xml.stream.XMLStreamException e) { throw new IllegalArgumentException("PMML 文档无效", e); }
    }
}
