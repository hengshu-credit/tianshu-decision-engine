package com.hengshucredit.rule.server.artifact;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class DecisionArtifactPackage {
    public static final String FORMAT_VERSION = "1";
    public static final String MANIFEST_PATH = "manifest.json";

    private final Map<String, Object> metadata = new LinkedHashMap<>();
    private final Map<String, Component> components = new LinkedHashMap<>();

    public void putMetadata(String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("制品元数据键不能为空");
        }
        if (value == null) {
            throw new IllegalArgumentException("制品元数据值不能为空: " + key);
        }
        metadata.put(key, value);
    }

    public void addComponent(String path, String mediaType, byte[] content) {
        addComponent(path, mediaType, content, Collections.emptyMap());
    }

    public void addComponent(String path, String mediaType, byte[] content,
                             Map<String, Object> componentMetadata) {
        addComponent(path, mediaType, content, componentMetadata, true);
    }

    // The decoder transfers ownership of a newly allocated entry. All public callers retain defensive copying.
    void addComponent(String path, String mediaType, byte[] content, Map<String, Object> componentMetadata, boolean copy) {
        validateComponentPath(path);
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("制品组件媒体类型不能为空: " + path);
        }
        if (content == null) {
            throw new IllegalArgumentException("制品组件内容不能为空: " + path);
        }
        if (components.containsKey(path)) {
            throw new IllegalArgumentException("制品组件路径重复: " + path);
        }
        components.put(path, new Component(path, mediaType, copy ? content.clone() : content, componentMetadata));
    }

    /** The transfer bundle already owns its immutable Base64 source. Decode one bounded block at a time. */
    public void addBase64Component(String path, String mediaType, String base64) {
        try {
            var hash = java.security.MessageDigest.getInstance("SHA-256");
            int size = writeBase64(base64, new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), hash));
            addDeferredModel(path, mediaType, size, java.util.HexFormat.of().formatHex(hash.digest()), Map.of(),
                    () -> java.util.Base64.getDecoder().decode(base64), output -> writeBase64(base64, output));
        } catch (java.security.NoSuchAlgorithmException | java.io.IOException failure) {
            throw new IllegalStateException("无法计算迁移组件摘要", failure);
        }
    }

    private static int writeBase64(String value, java.io.OutputStream sink) throws java.io.IOException {
        int padding = value.indexOf('=');
        if (padding >= 0 && padding < value.length() - 2)
            throw new IllegalArgumentException("Base64 组件尾部填充无效");
        var decoder = java.util.Base64.getDecoder();
        int count = 0;
        int offset = 0;
        while (offset < value.length()) {
            int end = offset + Math.min(64 * 1024, value.length() - offset);
            byte[] chunk = decoder.decode(value.substring(offset, end));
            sink.write(chunk);
            count = Math.addExact(count, chunk.length);
            offset = end;
        }
        return count;
    }

    @FunctionalInterface interface PayloadWriter { void write(java.io.OutputStream sink) throws java.io.IOException; }

    void addDeferredModel(String path, String mediaType, int size, String digest, Map<String, Object> metadata,
                          java.util.function.Supplier<byte[]> verifiedPayload) {
        addDeferredModel(path, mediaType, size, digest, metadata, verifiedPayload, null);
    }

    void addDeferredModel(String path, String mediaType, int size, String digest, Map<String, Object> metadata,
                          java.util.function.Supplier<byte[]> verifiedPayload, PayloadWriter streamingWriter) {
        validateComponentPath(path);
        if (components.putIfAbsent(path, new Component(path, mediaType, null, metadata, size, digest, verifiedPayload, streamingWriter)) != null)
            throw new IllegalArgumentException("制品组件路径重复: " + path);
    }

    void addModelDescriptor(String path, String mediaType, long size, String digest, Map<String, Object> metadata) {
        if (!"MODEL".equals(metadata.get("resourceType"))) throw new IllegalArgumentException("模型描述缺少组件类型");
        components.put(path, new Component(path, mediaType, null, metadata, Math.toIntExact(size), digest, null, null));
    }

    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public Map<String, Component> getComponents() {
        return Collections.unmodifiableMap(components);
    }

    public Component getComponent(String path) {
        return components.get(path);
    }

    static void validateComponentPath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.startsWith("\\")
                || path.contains("\\") || path.equals(MANIFEST_PATH)) {
            throw new IllegalArgumentException("制品组件路径无效: " + path);
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("制品组件路径无效: " + path);
            }
        }
    }

    public static final class Component {
        private final String path;
        private final String mediaType;
        private final byte[] content;
        private final String digest;
        private final int size;
        private final PayloadWriter streamingWriter;
        private final java.util.function.Supplier<byte[]> verifiedPayload;
        private final Map<String, Object> metadata;

        private Component(String path, String mediaType, byte[] content,
                          Map<String, Object> metadata) {
            this(path, mediaType, content, metadata, content.length, Sha256Digests.bytes(content), null, null);
        }

        private Component(String path, String mediaType, byte[] content, Map<String, Object> metadata, int size, String digest,
                          java.util.function.Supplier<byte[]> verifiedPayload, PayloadWriter streamingWriter) {
            this.streamingWriter = streamingWriter;
            this.verifiedPayload = verifiedPayload;
            this.path = path;
            this.mediaType = mediaType;
            this.content = content;
            this.digest = digest;
            this.size = size;
            this.metadata = metadata == null ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }

        public String getPath() {
            return path;
        }

        public String getMediaType() {
            return mediaType;
        }

        // Shared only with immutable artifact dependencies. Public callers receive a copy.
        byte[] immutableContent() {
            if (verifiedPayload != null) return verifiedPayload.get();
            if (content == null) throw new IllegalStateException("仅引用目录可使用无权重的模型描述");
            return content;
        }

        public int size() { return size; }

        public String digest() { return digest; }

        void writeTo(java.io.OutputStream output) throws java.io.IOException {
            if (streamingWriter != null) streamingWriter.write(output);
            else output.write(immutableContent());
        }

        public byte[] getContent() {
            return verifiedPayload == null ? immutableContent().clone() : verifiedPayload.get();
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}
