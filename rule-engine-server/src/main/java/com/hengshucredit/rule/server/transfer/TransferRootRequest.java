package com.hengshucredit.rule.server.transfer;

public record TransferRootRequest(String resourceType, Long resourceId) {
    public TransferKey key() {
        try {
            TransferResourceType type = TransferResourceType.valueOf(resourceType.trim().toUpperCase());
            if (type == TransferResourceType.LIST_RECORD_BATCH)
                throw new IllegalArgumentException("名单记录只能通过名单内容导出，不能进入配置迁移包");
            return new TransferKey(type, resourceId);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("离线导出资源类型或 ID 无效", invalid);
        }
    }
}
