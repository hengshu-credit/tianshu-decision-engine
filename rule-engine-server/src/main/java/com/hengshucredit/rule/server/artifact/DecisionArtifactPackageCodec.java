package com.hengshucredit.rule.server.artifact;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DecisionArtifactPackageCodec {
    private static final int MAX_ENTRIES = 10_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L;
    private final int maxEntries;
    private final long maxUncompressedBytes;

    public DecisionArtifactPackageCodec() {
        this(MAX_ENTRIES, MAX_UNCOMPRESSED_BYTES);
    }

    DecisionArtifactPackageCodec(int maxEntries, long maxUncompressedBytes) {
        if (maxEntries <= 0 || maxUncompressedBytes <= 0) {
            throw new IllegalArgumentException("决策制品解包限制必须为正数");
        }
        this.maxEntries = maxEntries;
        this.maxUncompressedBytes = maxUncompressedBytes;
    }

    public byte[] encode(DecisionArtifactPackage artifactPackage) {
        return encodePackage(artifactPackage, false).bytes();
    }

    record Encoded(byte[] bytes, String artifactDigest, String packageDigest, long size, boolean stored) {
        private Encoded(byte[] bytes, String artifactDigest) {
            this(bytes, artifactDigest, Sha256Digests.bytes(bytes), bytes.length, false);
        }
    }

    Encoded encodePackage(DecisionArtifactPackage artifactPackage, boolean enforceLimits) {
        return encodePackage(artifactPackage, enforceLimits, null);
    }

    Encoded encodePackage(DecisionArtifactPackage artifactPackage, boolean enforceLimits,
                          com.hengshucredit.rule.server.service.ModelFileStore storage) {
        Map<String, Object> manifest = baseManifest(artifactPackage);
        String artifactDigest = Sha256Digests.bytes(CanonicalJson.writeBytes(manifest));
        manifest.put("artifactDigest", artifactDigest);

        byte[] manifestBytes = CanonicalJson.writeBytes(manifest);
        long size = manifestBytes.length + artifactPackage.getComponents().values().stream()
                .mapToLong(DecisionArtifactPackage.Component::size).sum();
        if (enforceLimits) {
            if (artifactPackage.getComponents().size() + 1 > maxEntries)
                throw new IllegalArgumentException("决策制品包条目数量超过限制");
            if (size > maxUncompressedBytes) throw new IllegalArgumentException("决策制品包解压大小超过限制");
        }
        try {
            if (size <= 64L * 1024 * 1024) {
                var buffer = new ByteArrayOutputStream();
                writePackage(artifactPackage, manifestBytes, buffer);
                return new Encoded(buffer.toByteArray(), artifactDigest);
            }
            // Avoid holding a growing ZIP buffer and a second contiguous ZIP alongside the current ONNX weight.
            var spool = java.nio.file.Files.createTempFile("tianshu-publish-", ".zip");
            try {
                MessageDigest hash = sha256();
                writePackage(artifactPackage, manifestBytes, new java.security.DigestOutputStream(java.nio.file.Files.newOutputStream(spool), hash));
                if (storage != null && storage.storesSharedArchive(artifactPackage)) {
                    int length = Math.toIntExact(java.nio.file.Files.size(spool));
                    String digest = java.util.HexFormat.of().formatHex(hash.digest());
                    return new Encoded(storage.storeArchive(artifactPackage, spool, digest, length), artifactDigest, digest, length, true);
                }
                return new Encoded(java.nio.file.Files.readAllBytes(spool), artifactDigest);
            } finally {
                java.nio.file.Files.deleteIfExists(spool);
            }
        } catch (IOException error) {
            throw new IllegalStateException("无法生成决策制品包", error);
        }
    }

    private void writePackage(DecisionArtifactPackage artifactPackage, byte[] manifestBytes, java.io.OutputStream sink) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(new java.io.BufferedOutputStream(sink))) {
            output.setLevel(9);
            openEntry(output, DecisionArtifactPackage.MANIFEST_PATH);
            output.write(manifestBytes);
            output.closeEntry();
            for (DecisionArtifactPackage.Component component
                    : new TreeMap<>(artifactPackage.getComponents()).values()) {
                openEntry(output, component.getPath());
                component.writeTo(output);
                output.closeEntry();
            }
        }
    }

    public DecodedPackage decode(byte[] packageBytes) {
        return decode(packageBytes, null);
    }

    /** Verify every ZIP entry, retaining payload bytes only for the requested projection. */
    DecodedPackage decode(byte[] packageBytes, Set<String> retainedPaths) {
        return decode(packageBytes, retainedPaths, false, false);
    }

    DecodedPackage decodeCatalog(byte[] packageBytes) {
        return decode(packageBytes, null, true, false);
    }

    /** Materialize only when assembling the approved artifact, with a single exact-sized weight buffer. */
    byte[] readVerifiedModel(byte[] archive, DecisionArtifactPackage.Component verified) {
        return readVerifiedModel(archive, verified.getPath(), verified.size(), verified.digest());
    }

    void writeVerifiedModel(byte[] archive, DecisionArtifactPackage.Component verified, java.io.OutputStream sink) throws IOException {
        try (var zip = new org.apache.commons.compress.archivers.zip.ZipFile(
                new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(archive))) {
            var entry = zip.getEntry(verified.getPath());
            if (entry == null || entry.getSize() != verified.size()) throw new IOException("冻结模型大小不匹配");
            MessageDigest hash = sha256();
            try (var input = new java.security.DigestInputStream(zip.getInputStream(entry), hash)) {
                byte[] block = new byte[65536];
                long remaining = entry.getSize();
                while (remaining > 0) {
                    int count = input.read(block, 0, (int)Math.min(remaining, block.length));
                    if (count <= 0) throw new IOException("冻结模型权重读取不完整");
                    sink.write(block, 0, count);
                    remaining -= count;
                }
                if (input.read() != -1 || !sameDigest(verified.digest(), java.util.HexFormat.of().formatHex(hash.digest())))
                    throw new IOException("冻结模型内容与已验证组件不一致");
            }
        }
    }

    private byte[] readVerifiedModel(byte[] archive, String path, int size, String digest) {
        try (var zip = new org.apache.commons.compress.archivers.zip.ZipFile(
                new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(archive))) {
            var entry = zip.getEntry(path);
            if (entry == null || entry.getSize() != size) throw new IllegalArgumentException("冻结模型大小不匹配");
            try (var input = zip.getInputStream(entry)) {
                byte[] bytes = new byte[size];
                if (input.readNBytes(bytes, 0, bytes.length) != bytes.length || input.read() != -1
                        || !sameDigest(digest, Sha256Digests.bytes(bytes)))
                    throw new IllegalArgumentException("冻结模型内容与已验证组件不一致");
                return bytes;
            }
        } catch (IOException error) { throw new IllegalArgumentException("无法读取冻结模型组件", error); }
    }

    /** Server-owned ZIP bytes stay with the snapshot. Large weights are verified now and inflated individually on use. */
    DecodedPackage decodeRuntime(byte[] packageBytes) {
        return decode(packageBytes, null, false, true);
    }

    private DecodedPackage decode(byte[] packageBytes, Set<String> retainedPaths, boolean catalog, boolean lazyModels) {
        if (packageBytes == null || packageBytes.length == 0) {
            throw new IllegalArgumentException("决策制品包不能为空");
        }
        Map<String, VerifiedEntry> entries = readEntries(packageBytes, retainedPaths, catalog);
        VerifiedEntry manifestEntry = entries.remove(DecisionArtifactPackage.MANIFEST_PATH);
        if (manifestEntry == null) {
            throw new IllegalArgumentException("决策制品包缺少 manifest.json");
        }

        Map<String, Object> manifest = CanonicalJson.readMap(manifestEntry.content());
        if (!DecisionArtifactPackage.FORMAT_VERSION.equals(String.valueOf(manifest.get("formatVersion")))) {
            throw new IllegalArgumentException("不支持的决策制品格式版本: " + manifest.get("formatVersion"));
        }
        Object digestValue = manifest.remove("artifactDigest");
        if (!(digestValue instanceof String expectedArtifactDigest) || expectedArtifactDigest.length() != 64) {
            throw new IllegalArgumentException("决策制品清单缺少有效摘要");
        }
        String actualArtifactDigest = Sha256Digests.bytes(CanonicalJson.writeBytes(manifest));
        if (!sameDigest(expectedArtifactDigest, actualArtifactDigest)) {
            throw new IllegalArgumentException("决策制品清单摘要校验失败");
        }

        DecisionArtifactPackage artifactPackage = new DecisionArtifactPackage();
        Object metadataValue = manifest.get("metadata");
        if (!(metadataValue instanceof Map<?, ?> metadata)) {
            throw new IllegalArgumentException("决策制品清单 metadata 无效");
        }
        for (Map.Entry<?, ?> entry : metadata.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("决策制品清单 metadata 键无效");
            }
            artifactPackage.putMetadata(key, entry.getValue());
        }

        Object componentsValue = manifest.get("components");
        if (!(componentsValue instanceof List<?> components)) {
            throw new IllegalArgumentException("决策制品清单 components 无效");
        }
        Set<String> declaredPaths = new HashSet<>();
        for (Object value : components) {
            if (!(value instanceof Map<?, ?> component)) {
                throw new IllegalArgumentException("决策制品组件清单无效");
            }
            String path = requiredText(component, "path");
            String mediaType = requiredText(component, "mediaType");
            String expectedDigest = requiredText(component, "digest");
            long expectedSize = requiredLong(component, "size");
            Map<String, Object> componentMetadata = optionalMap(component.get("metadata"));
            DecisionArtifactPackage.validateComponentPath(path);
            if (!declaredPaths.add(path)) {
                throw new IllegalArgumentException("决策制品组件路径重复: " + path);
            }
            VerifiedEntry entry = entries.get(path);
            if (entry == null) {
                throw new IllegalArgumentException("决策制品组件缺失: " + path);
            }
            if (entry.size() != expectedSize) {
                throw new IllegalArgumentException("决策制品组件大小校验失败: " + path);
            }
            if (entry.content() != null) artifactPackage.addComponent(path, mediaType, entry.content(), componentMetadata, false);
            String digest = entry.content() == null ? entry.digest() : artifactPackage.getComponent(path).digest();
            if (entry.content() == null && retainedPaths == null) {
                if (catalog) artifactPackage.addModelDescriptor(path, mediaType, entry.size(), digest, componentMetadata);
                else if (lazyModels) artifactPackage.addDeferredModel(path, mediaType, Math.toIntExact(entry.size()), digest,
                        componentMetadata, () -> readVerifiedModel(packageBytes, path, Math.toIntExact(entry.size()), digest));
                else artifactPackage.addComponent(path, mediaType, readVerifiedModel(packageBytes, path, Math.toIntExact(entry.size()), digest),
                        componentMetadata, false);
            }
            if (!sameDigest(expectedDigest, digest)) {
                throw new IllegalArgumentException("决策制品组件摘要校验失败: " + path);
            }
        }
        if (!declaredPaths.equals(entries.keySet())) {
            Set<String> unexpected = new HashSet<>(entries.keySet());
            unexpected.removeAll(declaredPaths);
            throw new IllegalArgumentException("决策制品包包含未声明组件: " + unexpected);
        }
        return new DecodedPackage(artifactPackage, expectedArtifactDigest,
                Sha256Digests.bytes(packageBytes));
    }

    private Map<String, Object> baseManifest(DecisionArtifactPackage artifactPackage) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("formatVersion", DecisionArtifactPackage.FORMAT_VERSION);
        manifest.put("metadata", new TreeMap<>(artifactPackage.getMetadata()));
        List<Map<String, Object>> components = new ArrayList<>();
        for (DecisionArtifactPackage.Component component
                : new TreeMap<>(artifactPackage.getComponents()).values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", component.getPath());
            item.put("mediaType", component.getMediaType());
            item.put("size", component.size());
            item.put("digest", component.digest());
            if (!component.getMetadata().isEmpty()) {
                item.put("metadata", component.getMetadata());
            }
            components.add(item);
        }
        manifest.put("components", components);
        return manifest;
    }

    private record VerifiedEntry(byte[] content, long size, String digest) { }

    private Map<String, VerifiedEntry> readEntries(byte[] packageBytes, Set<String> retainedPaths, boolean catalog) {
        Map<String, VerifiedEntry> entries = new TreeMap<>();
        long totalBytes = 0L;
        Set<String> models = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(packageBytes))) {
            byte[] chunk = new byte[65536];
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    throw new IllegalArgumentException("决策制品包不允许目录条目: " + entry.getName());
                }
                String path = entry.getName();
                if (!DecisionArtifactPackage.MANIFEST_PATH.equals(path)) {
                    DecisionArtifactPackage.validateComponentPath(path);
                }
                if (entries.size() >= maxEntries) {
                    throw new IllegalArgumentException("决策制品包条目数量超过限制");
                }
                boolean retain = !models.contains(path) && (retainedPaths == null || retainedPaths.contains(path)
                        || DecisionArtifactPackage.MANIFEST_PATH.equals(path));
                ByteArrayOutputStream entryBuffer = retain ? new ByteArrayOutputStream() : null;
                MessageDigest digest = retain ? null : sha256();
                long size = 0;
                int read;
                while ((read = input.read(chunk)) != -1) {
                    if (totalBytes + size + read > maxUncompressedBytes) {
                        throw new IllegalArgumentException("决策制品包解压大小超过限制");
                    }
                    if (retain) entryBuffer.write(chunk, 0, read);
                    else digest.update(chunk, 0, read);
                    size += read;
                }
                VerifiedEntry verified = retain ? new VerifiedEntry(entryBuffer.toByteArray(), size, null)
                        : new VerifiedEntry(null, size, java.util.HexFormat.of().formatHex(digest.digest()));
                if ((catalog || retainedPaths == null) && DecisionArtifactPackage.MANIFEST_PATH.equals(path)
                        && CanonicalJson.readMap(verified.content()).get("components") instanceof List<?> declared) {
                    for (Object item : declared) {
                        if (item instanceof Map<?, ?> part && part.get("metadata") instanceof Map<?, ?> meta
                                && "MODEL".equals(meta.get("resourceType")) && part.get("path") instanceof String modelPath
                                && (catalog || part.get("size") instanceof Number n && n.longValue() >= 8L * 1024 * 1024))
                            models.add(modelPath);
                    }
                }
                totalBytes += size;
                if (entries.put(path, verified) != null) {
                    throw new IllegalArgumentException("决策制品包包含重复路径: " + path);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("决策制品包格式无效", e);
        }
        return entries;
    }

    private MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private void openEntry(ZipOutputStream output, String path) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(0L);
        output.putNextEntry(entry);
    }

    private String requiredText(Map<?, ?> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("决策制品组件 " + key + " 无效");
        }
        return text;
    }

    private long requiredLong(Map<?, ?> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof Number number) || number.longValue() < 0L) {
            throw new IllegalArgumentException("决策制品组件 " + key + " 无效");
        }
        return number.longValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> optionalMap(Object value) {
        if (value == null) return java.util.Collections.emptyMap();
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("决策制品组件 metadata 无效");
        }
        return (Map<String, Object>) value;
    }

    private boolean sameDigest(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    public static final class DecodedPackage {
        private final DecisionArtifactPackage artifactPackage;
        private final String artifactDigest;
        private final String packageDigest;

        private DecodedPackage(DecisionArtifactPackage artifactPackage,
                               String artifactDigest, String packageDigest) {
            this.artifactPackage = artifactPackage;
            this.artifactDigest = artifactDigest;
            this.packageDigest = packageDigest;
        }

        public DecisionArtifactPackage getArtifactPackage() {
            return artifactPackage;
        }

        public String getArtifactDigest() {
            return artifactDigest;
        }

        public String getPackageDigest() {
            return packageDigest;
        }
    }
}
