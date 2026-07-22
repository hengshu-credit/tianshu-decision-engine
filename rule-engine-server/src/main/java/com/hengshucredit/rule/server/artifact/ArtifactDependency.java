package com.hengshucredit.rule.server.artifact;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ArtifactDependency {
    private final String componentId;
    private final String resourceType;
    private final Long resourceId;
    private final Integer version;
    private final String componentPath;
    private final String mediaType;
    private final String embeddingMode;
    private final String contentDigest;
    private final byte[] content;
    private final Map<String, Object> metadata;

    public ArtifactDependency(String componentId, String resourceType, Long resourceId,
                              Integer version, String componentPath, String mediaType,
                              String embeddingMode, String contentDigest, byte[] content,
                              Map<String, Object> metadata) {
        this.componentId = componentId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.version = version;
        this.componentPath = componentPath;
        this.mediaType = mediaType;
        this.embeddingMode = embeddingMode;
        this.contentDigest = contentDigest;
        this.content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
        this.metadata = metadata == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public String getComponentId() {
        return componentId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public Integer getVersion() {
        return version;
    }

    public String getComponentPath() {
        return componentPath;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getEmbeddingMode() {
        return embeddingMode;
    }

    public String getContentDigest() {
        return contentDigest;
    }

    public byte[] getContent() {
        return Arrays.copyOf(content, content.length);
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
