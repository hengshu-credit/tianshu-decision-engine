package com.hengshucredit.rule.server.transfer;

public record TransferRootRequest(String resourceType, Long resourceId, String versionMode, Long versionId) {
    public TransferRootRequest(String resourceType, Long resourceId) { this(resourceType, resourceId, "LATEST", null); }
    public TransferKey key() {
        try {
            if (versionMode != null && !java.util.Set.of("LATEST", "FIXED").contains(versionMode))
                throw new IllegalArgumentException("迁移版本方式无效");
            if ("FIXED".equals(versionMode) && (versionId == null || versionId <= 0))
                throw new IllegalArgumentException("迁移指定版本必须提供版本 ID");
            TransferResourceType type = TransferResourceType.valueOf(resourceType.trim().toUpperCase());
            if (type == TransferResourceType.LIST_RECORD_BATCH)
                throw new IllegalArgumentException("名单记录只能通过名单内容导出，不能进入配置迁移包");
            return new TransferKey(type, resourceId);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("离线导出资源类型或 ID 无效", invalid);
        }
    }
}
