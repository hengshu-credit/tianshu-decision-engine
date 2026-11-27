package com.hengshucredit.rule.server.artifact;

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
    private final java.util.function.Supplier<byte[]> payload;
    private final int frozenModelSize;
    private final DecisionArtifactPackage.PayloadWriter streamingWriter;
    private final Map<String, Object> metadata;

    public ArtifactDependency(String componentId, String resourceType, Long resourceId,
                              Integer version, String componentPath, String mediaType,
                              String embeddingMode, String contentDigest, byte[] content,
                              Map<String, Object> metadata) {
        this(componentId, resourceType, resourceId, version, componentPath, mediaType, embeddingMode, contentDigest,
                metadata, immutablePayload(content), -1, null);
    }

    private ArtifactDependency(String componentId, String resourceType, Long resourceId, Integer version,
                               String componentPath, String mediaType, String embeddingMode, String contentDigest,
                               Map<String, Object> metadata, java.util.function.Supplier<byte[]> payload, int frozenModelSize, DecisionArtifactPackage.PayloadWriter streamingWriter) {
        this.streamingWriter = streamingWriter;
        this.frozenModelSize = frozenModelSize;
        this.componentId = componentId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.version = version;
        this.componentPath = componentPath;
        this.mediaType = mediaType;
        this.embeddingMode = embeddingMode;
        this.contentDigest = contentDigest;
        this.payload = payload;
        this.metadata = metadata == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    private static java.util.function.Supplier<byte[]> immutablePayload(byte[] content) {
        byte[] frozen = content == null ? new byte[0] : content.clone();
        return () -> frozen;
    }

    static ArtifactDependency frozen(String key, String type, Long id, Integer version, String mode,
                                     DecisionArtifactPackage.Component component, Map<String, Object> metadata, java.util.function.Supplier<byte[]> payload,
                                     DecisionArtifactPackage.PayloadWriter streamingWriter) {
        return new ArtifactDependency(key, type, id, version, component.getPath(), component.getMediaType(),
                mode, component.digest(), metadata, payload, "MODEL".equals(type) ? component.size() : -1, streamingWriter);
    }

    public void addTo(DecisionArtifactPackage target, Map<String, Object> portableMetadata) {
        if (frozenModelSize >= 0) target.addDeferredModel(componentPath, mediaType, frozenModelSize, contentDigest, portableMetadata, payload, streamingWriter);
        else target.addComponent(componentPath, mediaType, payload.get(), portableMetadata, false);
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
        return payload.get().clone();
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
