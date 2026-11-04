package com.hengshucredit.rule.model.dto;

public final class LedgerRequests {
    private LedgerRequests() { }
    public record SaveDraft(Long version, LedgerContent content) { }
    public record Publish(Long version) { }
    public record Event(String title, String status, String scope, Long projectId, Long version) { }
    public record Asset(String name, String scope, Long projectId, Long version) { }
}
