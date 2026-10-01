package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.model.entity.OfflineTransferLog;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.mapper.OfflineTransferLogMapper;
import com.hengshucredit.rule.server.transfer.TransferBundleCodec;
import com.hengshucredit.rule.server.transfer.TransferBundle;
import com.hengshucredit.rule.server.transfer.TransferRootRequest;
import com.hengshucredit.rule.server.transfer.TransferImportOptions;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OfflineTransferLogService extends ServiceImpl<OfflineTransferLogMapper, OfflineTransferLog> {
    private final TransferBundleCodec codec = new TransferBundleCodec();

    public void recordExport(List<TransferRootRequest> roots, byte[] bytes, String operator) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("roots", roots == null ? List.of() : roots);
        try { content.put("lineage", lineageSnapshot(codec.decode(bytes).bundle(), null)); }
        catch (RuntimeException ignored) { }
        saveLog("EXPORT", "SUCCESS", operator, Sha256Digests.bytes(bytes),
                roots == null ? List.of() : roots.stream().map(root -> root.resourceType() + ":" + root.resourceId()).toList(),
                roots == null ? 0 : roots.size(), content, null);
    }

    public void recordImport(byte[] bytes, Map<String, Object> result, String operator, String errorMessage) {
        TransferBundleCodec.Decoded decoded = null;
        try { decoded = codec.decode(bytes); } catch (RuntimeException ignored) { }
        List<String> roots = decoded == null ? List.of() : decoded.bundle().roots();
        int count = decoded == null ? 0 : decoded.bundle().resources().size();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("options", result == null ? Map.of() : result.getOrDefault("options", Map.of()));
        content.put("result", result == null ? Map.of() : result);
        if (decoded != null) {
            List<String> selected = selectedKeys(result);
            content.put("lineage", lineageSnapshot(decoded.bundle(), selected));
        }
        saveLog("IMPORT", errorMessage == null ? "SUCCESS" : "FAILED", operator,
                Sha256Digests.bytes(bytes), roots, count, content, errorMessage);
    }

    public void recordFailure(String operationType, List<TransferRootRequest> roots, String operator, String errorMessage) {
        List<String> keys = roots == null ? List.of() : roots.stream()
                .map(root -> root.resourceType() + ":" + root.resourceId()).toList();
        saveLog(operationType, "FAILED", operator, null, keys, keys.size(), Map.of("roots", keys), errorMessage);
    }

    public IPage<OfflineTransferLog> page(int pageNum, int pageSize, String operationType) {
        LambdaQueryWrapper<OfflineTransferLog> query = new LambdaQueryWrapper<>();
        if (operationType != null && !operationType.isBlank()) query.eq(OfflineTransferLog::getOperationType, operationType);
        query.orderByDesc(OfflineTransferLog::getCreateTime);
        return page(new Page<>(pageNum, pageSize), query);
    }

    public Map<String, Object> lineage(Long logId) {
        OfflineTransferLog log = getById(logId);
        if (log == null || log.getContentJson() == null || log.getContentJson().isBlank()) return emptyLineage();
        try {
            Map<String, Object> content = com.hengshucredit.rule.server.artifact.CanonicalJson.readMap(log.getContentJson());
            Object value = content.get("lineage");
            return value instanceof Map<?, ?> map
                    ? normalizeLineage(com.hengshucredit.rule.server.artifact.CanonicalJson.readMap(
                    com.alibaba.fastjson.JSON.toJSONString(map)))
                    : emptyLineage();
        } catch (RuntimeException ignored) {
            return emptyLineage();
        }
    }

    private List<String> selectedKeys(Map<String, Object> result) {
        if (result == null) return null;
        Object options = result.get("options");
        if (options instanceof TransferImportOptions parsed
                && parsed.selectedResourceKeys() != null && !parsed.selectedResourceKeys().isEmpty()) {
            return parsed.selectedResourceKeys();
        }
        if (!(options instanceof Map<?, ?> map)) return null;
        Object value = map.get("selectedResourceKeys");
        if (!(value instanceof List<?> list) || list.isEmpty()) return null;
        return list.stream().map(String::valueOf).toList();
    }

    private Map<String, Object> lineageSnapshot(TransferBundle bundle, List<String> selectedKeys) {
        java.util.Set<String> selected = selectedKeys == null || selectedKeys.isEmpty()
                ? bundle.resources().stream().map(TransferBundle.Resource::key).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))
                : new java.util.LinkedHashSet<>(selectedKeys);
        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        List<Map<String, Object>> edges = new java.util.ArrayList<>();
        for (TransferBundle.Resource resource : bundle.resources()) {
            if (!selected.contains(resource.key())) continue;
            var key = com.hengshucredit.rule.server.transfer.TransferKey.parse(resource.key());
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", resource.key());
            node.put("type", graphType(key.type().name()));
            node.put("label", resource.configuration().getOrDefault(key.type().codeField, resource.key()));
            node.put("code", resource.configuration().get(key.type().codeField));
            node.put("refId", key.id());
            nodes.add(node);
            for (TransferBundle.Reference reference : resource.references()) {
                if (!selected.contains(reference.targetKey())) continue;
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("from", resource.key()); edge.put("to", reference.targetKey());
                edge.put("label", reference.path() == null ? "依赖" : reference.path());
                edges.add(edge);
            }
        }
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes); graph.put("edges", edges);
        graph.put("startNode", nodes.isEmpty() ? null : nodes.get(0));
        return graph;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeLineage(Map<String, Object> source) {
        List<Map<String, Object>> nodes = source.get("nodes") instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList() : List.of();
        java.util.Set<String> nodeIds = nodes.stream().map(item -> String.valueOf(item.get("id")))
                .collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> edges = source.get("edges") instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item)
                .filter(item -> nodeIds.contains(String.valueOf(item.get("from"))) && nodeIds.contains(String.valueOf(item.get("to"))))
                .toList() : List.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes); result.put("edges", edges);
        result.put("startNode", nodes.isEmpty() ? null : nodes.get(0));
        return result;
    }

    private String graphType(String type) {
        return switch (type) {
            case "DATABASE" -> "DB";
            case "EXTERNAL_DATASOURCE" -> "DATASOURCE";
            case "EXTERNAL_API" -> "API";
            case "LIST_LIBRARY" -> "LIST";
            default -> type;
        };
    }

    private Map<String, Object> emptyLineage() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", List.of()); result.put("edges", List.of()); result.put("startNode", null);
        return result;
    }

    private void saveLog(String operationType, String status, String operator, String digest,
                         List<String> roots, int count, Map<String, Object> content, String error) {
        OfflineTransferLog log = new OfflineTransferLog();
        log.setOperationType(operationType); log.setStatus(status); log.setOperator(operator);
        log.setPackageDigest(digest); log.setRootsJson(JSON.toJSONString(roots)); log.setResourceCount(count);
        log.setContentJson(JSON.toJSONString(content)); log.setErrorMessage(error); save(log);
    }
}
