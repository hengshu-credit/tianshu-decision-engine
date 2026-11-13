package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.auth.ProjectAuthContext;

/** Durable execution options. No token, signature or credential secret is persisted. */
public record ExecutionDispatch(Long authId, String globalKeyId, Long tokenId, String clientAppName,
                                boolean traceEnabled, Boolean recordTrace, boolean openContract, String externalRecoveryMode) {
    public static ExecutionDispatch of(ProjectAuthContext auth, String appName, boolean traceEnabled, String mode) {
        return new ExecutionDispatch(auth.getAuthId(), auth.isGlobalAccess() ? auth.getAuthCode() : null,
                auth.getTokenId(), appName, traceEnabled, traceEnabled, false, mode);
    }

    public static ExecutionDispatch open(ProjectAuthContext auth, boolean collect, boolean record) {
        return new ExecutionDispatch(auth.getAuthId(), null, auth.getTokenId(), "OPEN_API", collect, record, true, null);
    }
}
