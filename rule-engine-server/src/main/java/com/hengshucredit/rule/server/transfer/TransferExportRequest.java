package com.hengshucredit.rule.server.transfer;

import java.util.List;

/** 离线导出选项；默认递归带出上游，关闭后只保留用户选中的根资源。 */
public record TransferExportRequest(List<TransferRootRequest> roots,
                                    Boolean includeDependencies) {
    public boolean recursive() {
        return includeDependencies == null || includeDependencies;
    }
}
