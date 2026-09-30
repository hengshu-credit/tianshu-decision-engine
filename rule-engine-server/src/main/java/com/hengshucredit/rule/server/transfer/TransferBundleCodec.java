package com.hengshucredit.rule.server.transfer;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.DecisionArtifactPackage;
import com.hengshucredit.rule.server.artifact.DecisionArtifactPackageCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 仅在用户导出请求中编码；复用 ZIP 完整性校验与解压预算，不写服务器文件或制品表。 */
public final class TransferBundleCodec {
    public static final String PACKAGE_KIND = "TIANSHU_RESOURCE_TRANSFER";
    private final DecisionArtifactPackageCodec zip = new DecisionArtifactPackageCodec();

    public byte[] encode(TransferBundle bundle) {
        validate(bundle);
        DecisionArtifactPackage archive = new DecisionArtifactPackage();
        archive.putMetadata("packageKind", PACKAGE_KIND);
        archive.putMetadata("resourceFormatVersion", "1");
        archive.putMetadata("roots", bundle.roots());
        archive.putMetadata("warnings", bundle.warnings());
        for (var resource : bundle.resources()) {
            archive.addComponent(path(resource.key()), "application/json", CanonicalJson.writeBytes(resource));
        }
        return zip.encode(archive);
    }

    public Decoded decode(byte[] bytes) {
        var decoded = zip.decode(bytes);
        var archive = decoded.getArtifactPackage();
        if (!PACKAGE_KIND.equals(archive.getMetadata().get("packageKind"))
                || !"1".equals(archive.getMetadata().get("resourceFormatVersion")))
            throw new IllegalArgumentException("请选择天枢离线配置迁移包（版本 1）");
        List<TransferBundle.Resource> resources = new ArrayList<>();
        for (var component : archive.getComponents().values()) {
            Map<String, Object> value = CanonicalJson.readMap(component.getContent());
            String key = requiredText(value.get("key"));
            if (!path(key).equals(component.getPath()) || !"application/json".equals(component.getMediaType()))
                throw new IllegalArgumentException("迁移资源清单与文件路径不匹配: " + key);
            List<TransferBundle.Reference> references = new ArrayList<>();
            if (!(value.get("references") instanceof List<?> items)) throw new IllegalArgumentException("迁移引用清单缺失");
            for (Object item : items) {
                Map<String, Object> reference = object(item);
                references.add(new TransferBundle.Reference(requiredText(reference.get("path")),
                        requiredText(reference.get("targetKey")), optionalText(reference.get("childPath")),
                        Boolean.TRUE.equals(reference.get("external")),
                        optionalText(reference.get("targetCode")), optionalText(reference.get("targetName"))));
            }
            resources.add(new TransferBundle.Resource(key, object(value.get("configuration")), references,
                    strings(value.get("requiredEnvironmentFields"))));
        }
        TransferBundle bundle = new TransferBundle(strings(archive.getMetadata().get("roots")), resources,
                strings(archive.getMetadata().get("warnings")));
        validate(bundle);
        return new Decoded(bundle, decoded.getPackageDigest());
    }

    private void validate(TransferBundle bundle) {
        if (bundle == null || bundle.roots().isEmpty() || bundle.resources().isEmpty())
            throw new IllegalArgumentException("请选择至少一个导出资源");
        Set<String> keys = new LinkedHashSet<>();
        for (var resource : bundle.resources()) {
            if (!keys.add(resource.key())) throw new IllegalArgumentException("迁移资源重复: " + resource.key());
        }
        if (new LinkedHashSet<>(bundle.roots()).size() != bundle.roots().size() || !keys.containsAll(bundle.roots()))
            throw new IllegalArgumentException("迁移起点重复或未包含在离线包中");
        Map<String, TransferBundle.Resource> indexed = new LinkedHashMap<>();
        bundle.resources().forEach(resource -> indexed.put(resource.key(), resource));
        for (var resource : bundle.resources()) {
            Set<String> paths = new LinkedHashSet<>();
            for (var reference : resource.references()) {
                if (!paths.add(reference.path())) throw new IllegalArgumentException("迁移引用路径重复: " + resource.key() + reference.path());
                if (!reference.external() && !keys.contains(reference.targetKey())) {
                    throw new IllegalArgumentException("离线包缺少上游依赖: " + reference.targetKey());
                }
                TransferJsonPath.read(resource.configuration(), reference.path());
                if (reference.childPath() != null && !reference.external())
                    TransferJsonPath.read(indexed.get(reference.targetKey()).configuration(), reference.childPath());
            }
        }
    }

    private String path(String key) {
        TransferKey parsed = TransferKey.parse(key);
        return "resources/" + parsed.type().name() + "/" + parsed.id() + ".json";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("迁移资源内容必须是 JSON 对象");
        return (Map<String, Object>) value;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("迁移清单必须是数组");
        return list.stream().map(this::requiredText).toList();
    }
    private String requiredText(Object value) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("迁移清单缺少字符串字段");
        return text;
    }
    private String optionalText(Object value) { return value == null ? null : requiredText(value); }
    public record Decoded(TransferBundle bundle, String packageDigest) { }
}
