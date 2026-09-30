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
import com.hengshucredit.rule.server.transfer.TransferRootRequest;
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

    private void saveLog(String operationType, String status, String operator, String digest,
                         List<String> roots, int count, Map<String, Object> content, String error) {
        OfflineTransferLog log = new OfflineTransferLog();
        log.setOperationType(operationType); log.setStatus(status); log.setOperator(operator);
        log.setPackageDigest(digest); log.setRootsJson(JSON.toJSONString(roots)); log.setResourceCount(count);
        log.setContentJson(JSON.toJSONString(content)); log.setErrorMessage(error); save(log);
    }
}
