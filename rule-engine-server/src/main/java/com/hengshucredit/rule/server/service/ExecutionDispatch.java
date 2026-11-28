package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.auth.ProjectAuthContext;

/** Durable execution options. No token, signature or credential secret is persisted. */
public record ExecutionDispatch(Long authId, String globalKeyId, Long tokenId, String clientAppName,
                                boolean traceEnabled, Boolean recordTrace, boolean openContract, String externalRecoveryMode,
                                Long institutionId) {
    public ExecutionDispatch(Long authId, String globalKeyId, Long tokenId, String clientAppName,
                             boolean traceEnabled, Boolean recordTrace, boolean openContract, String externalRecoveryMode) {
        this(authId, globalKeyId, tokenId, clientAppName, traceEnabled, recordTrace, openContract, externalRecoveryMode, null);
    }

    /** 历史订单没有机构字段。机构订单必须同时匹配机构和长期凭据，续发令牌仍使用同一凭据。 */
    public static boolean sameCaller(ProjectAuthContext auth, String dispatchJson) {
        if (auth.isGlobalAccess()) return true;
        var saved = dispatchJson == null ? null : com.alibaba.fastjson.JSON.parseObject(dispatchJson);
        Long institution = saved == null ? null : saved.getLong("institutionId");
        if (institution == null) return !auth.isInstitutionAccess();
        return auth.isInstitutionAccess() && institution.equals(auth.getInstitutionId())
                && auth.getAuthId() != null && auth.getAuthId().equals(saved.getLong("authId"));
    }

    static String credentialKey(String digest, Long institutionId, Long authId) {
        if (institutionId == null) return digest;
        if (authId == null || authId <= 0) throw new IllegalArgumentException("机构调用缺少凭据标识");
        // 历史项目键始终为 64 位十六进制，非十六进制前缀使两种命名空间严格不相交。
        String scoped = com.hengshucredit.rule.server.auth.HmacRequestSigner.sha256Hex(
                (institutionId + ":" + authId + ":" + digest).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "I" + scoped.substring(1);
    }
    public static ExecutionDispatch of(ProjectAuthContext auth, String appName, boolean traceEnabled, String mode) {
        return new ExecutionDispatch(auth.getAuthId(), auth.isGlobalAccess() ? auth.getAuthCode() : null,
                auth.getTokenId(), appName, traceEnabled, traceEnabled, false, mode, auth.getInstitutionId());
    }

    public static ExecutionDispatch open(ProjectAuthContext auth, boolean collect, boolean record) {
        return new ExecutionDispatch(auth.getAuthId(), null, auth.getTokenId(), "OPEN_API", collect, record, true, null, auth.getInstitutionId());
    }
}
