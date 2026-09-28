package com.hengshucredit.rule.server.transfer;

public record TransferRootRequest(String resourceType, Long resourceId) {
    public TransferKey key() {
        try {
            return new TransferKey(TransferResourceType.valueOf(resourceType.trim().toUpperCase()), resourceId);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("离线导出资源类型或 ID 无效", invalid);
        }
    }
}
