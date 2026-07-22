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
        Map<String, Object> manifest = baseManifest(artifactPackage);
        String artifactDigest = Sha256Digests.bytes(CanonicalJson.writeBytes(manifest));
        manifest.put("artifactDigest", artifactDigest);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(buffer)) {
            output.setLevel(9);
            writeEntry(output, DecisionArtifactPackage.MANIFEST_PATH, CanonicalJson.writeBytes(manifest));
            for (DecisionArtifactPackage.Component component
                    : new TreeMap<>(artifactPackage.getComponents()).values()) {
                writeEntry(output, component.getPath(), component.getContent());
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法生成决策制品包", e);
        }
        return buffer.toByteArray();
    }

    @SuppressWarnings("unchecked")
    public DecodedPackage decode(byte[] packageBytes) {
        if (packageBytes == null || packageBytes.length == 0) {
            throw new IllegalArgumentException("决策制品包不能为空");
        }
        Map<String, byte[]> entries = readEntries(packageBytes);
        byte[] manifestBytes = entries.remove(DecisionArtifactPackage.MANIFEST_PATH);
        if (manifestBytes == null) {
            throw new IllegalArgumentException("决策制品包缺少 manifest.json");
        }

        Map<String, Object> manifest = CanonicalJson.readMap(manifestBytes);
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
            byte[] content = entries.get(path);
            if (content == null) {
                throw new IllegalArgumentException("决策制品组件缺失: " + path);
            }
            if (content.length != expectedSize) {
                throw new IllegalArgumentException("决策制品组件大小校验失败: " + path);
            }
            if (!sameDigest(expectedDigest, Sha256Digests.bytes(content))) {
                throw new IllegalArgumentException("决策制品组件摘要校验失败: " + path);
            }
            artifactPackage.addComponent(path, mediaType, content, componentMetadata);
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
            item.put("size", component.getContent().length);
            item.put("digest", Sha256Digests.bytes(component.getContent()));
            if (!component.getMetadata().isEmpty()) {
                item.put("metadata", component.getMetadata());
            }
            components.add(item);
        }
        manifest.put("components", components);
        return manifest;
    }

    private Map<String, byte[]> readEntries(byte[] packageBytes) {
        Map<String, byte[]> entries = new TreeMap<>();
        long totalBytes = 0L;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(packageBytes))) {
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
                ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = input.read(chunk)) != -1) {
                    if (totalBytes + entryBuffer.size() + read > maxUncompressedBytes) {
                        throw new IllegalArgumentException("决策制品包解压大小超过限制");
                    }
                    entryBuffer.write(chunk, 0, read);
                }
                byte[] content = entryBuffer.toByteArray();
                totalBytes += content.length;
                if (entries.put(path, content) != null) {
                    throw new IllegalArgumentException("决策制品包包含重复路径: " + path);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("决策制品包格式无效", e);
        }
        return entries;
    }

    private void writeEntry(ZipOutputStream output, String path, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(0L);
        output.putNextEntry(entry);
        output.write(content);
        output.closeEntry();
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
