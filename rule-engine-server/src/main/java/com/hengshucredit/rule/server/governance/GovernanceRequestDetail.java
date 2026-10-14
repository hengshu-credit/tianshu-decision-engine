package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.GovernanceApprovalEvent;

import java.util.List;

public record GovernanceRequestDetail(
        GovernanceRequestView request,
        ResourceDiff diff,
        List<GovernanceApprovalEvent> events,
        List<java.util.Map<String, Object>> dependencies,
        List<GovernanceVersionView> versions,
        boolean credentialChanged) {
}
