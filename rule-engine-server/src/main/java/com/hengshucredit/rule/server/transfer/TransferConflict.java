package com.hengshucredit.rule.server.transfer;

public record TransferConflict(String resourceKey, String resourceType,
                               String resourceCode, String conflictType,
                               String existingResourceKey, String recommendedAction,
                               String message) {
}
