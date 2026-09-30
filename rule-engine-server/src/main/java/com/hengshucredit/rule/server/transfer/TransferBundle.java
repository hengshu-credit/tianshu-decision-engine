package com.hengshucredit.rule.server.transfer;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import java.util.List;
import java.util.Map;

/** 离线迁移清单，不包含执行记录和名单记录。 */
public record TransferBundle(List<String> roots, List<Resource> resources, List<String> warnings) {
    public TransferBundle {
        roots = List.copyOf(roots);
        resources = List.copyOf(resources);
        warnings = List.copyOf(warnings);
    }

    public record Resource(String key, Map<String, Object> configuration, List<Reference> references,
                           List<String> requiredEnvironmentFields) {
        public Resource {
            TransferKey.parse(key);
            configuration = CanonicalJson.readMap(CanonicalJson.write(configuration));
            references = List.copyOf(references);
            requiredEnvironmentFields = List.copyOf(requiredEnvironmentFields);
        }
    }

    /**
     * path 使用 JSON Pointer；@json 表示进入字符串编码的 JSON；childPath 指向聚合中的子实体。
     * external 表示目标资源没有随本包携带，需要在导入面板中关联目标资源。
     */
    public record Reference(String path, String targetKey, String childPath,
                            boolean external, String targetCode, String targetName) {
        public Reference(String path, String targetKey, String childPath) {
            this(path, targetKey, childPath, false, null, null);
        }

        public Reference {
            if (path == null || !path.startsWith("/")) throw new IllegalArgumentException("迁移引用路径无效");
            TransferKey.parse(targetKey);
            if (childPath != null && !childPath.startsWith("/")) throw new IllegalArgumentException("迁移子资源路径无效");
        }

        public Reference asExternal() {
            return new Reference(path, targetKey, childPath, true, targetCode, targetName);
        }
    }
}
