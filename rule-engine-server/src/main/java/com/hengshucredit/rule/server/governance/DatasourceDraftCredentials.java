package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Uses the same editable workspace as the form, falling back to its saved projection. */
@Component
public class DatasourceDraftCredentials {
    private final GovernanceApprovalService approvals;
    private final GovernanceSecretCodec secrets;
    private final ConsoleOperatorResolver operators;

    // Only console draft trials need governance; normal runtime source resolution does not.
    public DatasourceDraftCredentials(@org.springframework.context.annotation.Lazy GovernanceApprovalService approvals,
                                     GovernanceSecretCodec secrets,
                                     ConsoleOperatorResolver operators) {
        this.approvals = approvals;
        this.secrets = secrets;
        this.operators = operators;
    }

    public Map<String, Object> load(String type, Long id) {
        ResourceSnapshot snapshot = approvals.editableCredentialSnapshot(type, id, operators.resolve());
        return snapshot == null ? Map.of() : secrets.restore(snapshot);
    }
}
