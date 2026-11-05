package com.hengshucredit.rule.model.dto;

public final class LedgerRequests {
    private LedgerRequests() { }
    public record SaveDraft(Long version, LedgerContent content) { }
    public record Publish(Long version) { }
    public record Event(String title, String status, String scope, Long projectId, Long version, Long typeId, java.util.List<StatusOption> statusOptions, String code) {
        public Event(String title, String status, String scope, Long projectId, Long version) { this(title, status, scope, projectId, version, null, null, null); }
        public Event(String title, String status, String scope, Long projectId, Long version, Long typeId) { this(title, status, scope, projectId, version, typeId, null, null); }
        public Event(String title, String status, String scope, Long projectId, Long version, Long typeId, java.util.List<StatusOption> options) { this(title, status, scope, projectId, version, typeId, options, null); }
    }
    public record StatusOption(String value, String label) { }
    public record EventType(String name, Integer sortOrder, Long version, String code) {
        public EventType(String name, Integer sortOrder, Long version) { this(name, sortOrder, version, null); }
    }
    public record Asset(String name, String scope, Long projectId, Long version) { }
}
