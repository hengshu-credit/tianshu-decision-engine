package com.hengshucredit.rule.server.artifact;

import java.util.Arrays;
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
        components.put(path, new Component(path, mediaType, content, componentMetadata));
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
        private final Map<String, Object> metadata;

        private Component(String path, String mediaType, byte[] content,
                          Map<String, Object> metadata) {
            this.path = path;
            this.mediaType = mediaType;
            this.content = Arrays.copyOf(content, content.length);
            this.metadata = metadata == null ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }

        public String getPath() {
            return path;
        }

        public String getMediaType() {
            return mediaType;
        }

        public byte[] getContent() {
            return Arrays.copyOf(content, content.length);
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}
