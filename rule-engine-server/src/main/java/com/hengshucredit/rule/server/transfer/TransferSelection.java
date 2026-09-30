package com.hengshucredit.rule.server.transfer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 将导入时的资源选择转换为一份可校验的子图；未选中的上游自动变成外部关联。 */
final class TransferSelection {
    private TransferSelection() { }

    static List<TransferBundle.Resource> select(TransferBundle bundle,
                                                TransferImportOptions options) {
        List<String> requested = options == null ? List.of() : options.normalizedSelectedResourceKeys();
        if (requested.isEmpty()) return bundle.resources();
        Set<String> available = bundle.resources().stream()
                .map(TransferBundle.Resource::key).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> selected = new LinkedHashSet<>(requested);
        if (!available.containsAll(selected)) {
            Set<String> unknown = new LinkedHashSet<>(selected);
            unknown.removeAll(available);
            throw new IllegalArgumentException("选择的迁移资源不存在: " + unknown);
        }
        List<TransferBundle.Resource> result = new ArrayList<>();
        for (TransferBundle.Resource resource : bundle.resources()) {
            if (!selected.contains(resource.key())) continue;
            List<TransferBundle.Reference> references = resource.references().stream()
                    .map(reference -> selected.contains(reference.targetKey())
                            ? reference : reference.asExternal())
                    .toList();
            result.add(new TransferBundle.Resource(resource.key(), resource.configuration(), references,
                    resource.requiredEnvironmentFields()));
        }
        return result;
    }

    static String referenceKey(String sourceKey, TransferBundle.Reference reference) {
        return sourceKey + "|" + reference.path() + "|" + reference.targetKey()
                + "|" + (reference.childPath() == null ? "" : reference.childPath());
    }

    static Map<String, TransferBundle.Resource> index(List<TransferBundle.Resource> resources) {
        return resources.stream().collect(java.util.stream.Collectors.toMap(
                TransferBundle.Resource::key, value -> value, (left, right) -> left,
                java.util.LinkedHashMap::new));
    }
}
