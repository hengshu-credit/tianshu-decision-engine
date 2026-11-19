package com.hengshucredit.rule.server.artifact;

import com.alibaba.fastjson.JSONObject;
import java.util.Map;

/** 模型制品只携带本机正式版本的绑定证明。凭据由执行环境恢复。 */
public final class ModelApiBindingComponent {
    public static final String TYPE = "FROZEN_MODEL_API";

    private ModelApiBindingComponent() { }

    public static String path(long apiId) { return "model-bindings/external-api/" + apiId + ".json"; }

    public static void add(DecisionArtifactPackage pack, long apiId, JSONObject descriptor) {
        pack.addComponent(path(apiId), "application/json", CanonicalJson.writeBytes(descriptor),
                Map.of("resourceType", TYPE, "componentId", TYPE + ":" + apiId, "resourceId", apiId, "embeddingMode", "EMBEDDED"));
    }

    static JSONObject read(DecisionArtifactPackage.Component component) {
        var descriptor = JSONObject.parseObject(component.getContent());
        Long id = descriptor == null ? null : descriptor.getLong("apiId");
        var metadata = component.getMetadata();
        if (id == null || id <= 0 || !path(id).equals(component.getPath())
                || !String.valueOf(id).equals(String.valueOf(metadata.get("resourceId")))
                || !(TYPE + ":" + id).equals(metadata.get("componentId")) || !TYPE.equals(metadata.get("resourceType"))) {
            throw new IllegalArgumentException("模型外数绑定的组件身份无效");
        }
        return descriptor;
    }

    static void verifyModelClosure(DecisionArtifactPackage pack) {
        for (var component : pack.getComponents().values()) {
            var metadata = component.getMetadata();
            if (!"EXPLICIT_BINDING".equals(metadata.get("embeddingMode"))) continue;
            long id = Long.parseLong(String.valueOf(metadata.get("resourceId")));
            var frozen = pack.getComponent(path(id));
            if (!("BINDING:EXTERNAL_API:" + id).equals(metadata.get("componentId")) || frozen == null) {
                throw new IllegalArgumentException("模型版本依赖未完整冻结: " + metadata.get("componentId"));
            }
            read(frozen);
        }
    }
}
