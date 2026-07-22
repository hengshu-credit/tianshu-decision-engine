package com.hengshucredit.rule.model.enums;

public enum RuleRevisionState {
    DRAFT,
    REVIEW,
    APPROVED,
    PUBLISHED,
    OFFLINE;

    public boolean canTransitionTo(RuleRevisionState target) {
        if (target == null) return false;
        if (this == DRAFT) return target == REVIEW;
        if (this == REVIEW) return target == DRAFT || target == APPROVED;
        if (this == APPROVED) return target == PUBLISHED;
        return this == PUBLISHED && target == OFFLINE;
    }
}
