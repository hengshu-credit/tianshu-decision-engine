package com.hengshucredit.rule.server.transfer;

/** Explicit target identity reviewed in preview; never infer it from a source ID or version number. */
public record TransferVersionBinding(Long resourceId, Long versionId, Long fieldId, Long expectedSnapshotId) { }
