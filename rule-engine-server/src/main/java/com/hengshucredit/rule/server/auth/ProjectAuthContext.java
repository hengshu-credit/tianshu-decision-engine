package com.hengshucredit.rule.server.auth;

import javax.servlet.http.HttpServletRequest;

public class ProjectAuthContext {
    public static final String REQUEST_ATTRIBUTE = ProjectAuthContext.class.getName();

    private final Long projectId;
    private final String projectCode;
    private final Long authId;
    private final String authCode;
    private final String authType;
    private final Long tokenId;
    private final String tokenCode;
    private final String authPhase;

    private ProjectAuthContext(Long projectId, String projectCode, Long authId, String authCode,
                               String authType, Long tokenId, String tokenCode, String authPhase) {
        this.projectId = projectId;
        this.projectCode = projectCode;
        this.authId = authId;
        this.authCode = authCode;
        this.authType = authType;
        this.tokenId = tokenId;
        this.tokenCode = tokenCode;
        this.authPhase = authPhase;
    }

    public static ProjectAuthContext direct(Long projectId, String projectCode, Long authId,
                                            String authCode, String authType) {
        return new ProjectAuthContext(projectId, projectCode, authId, authCode, authType,
                null, null, "DIRECT");
    }

    public static ProjectAuthContext temporary(Long projectId, String projectCode, Long authId,
                                               String authCode, String authType, Long tokenId,
                                               String tokenCode, String authPhase) {
        return new ProjectAuthContext(projectId, projectCode, authId, authCode, authType,
                tokenId, tokenCode, authPhase);
    }

    public void attach(HttpServletRequest request) {
        request.setAttribute(REQUEST_ATTRIBUTE, this);
        request.setAttribute("projectId", projectId);
        request.setAttribute("projectCode", projectCode);
    }

    public static ProjectAuthContext from(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(REQUEST_ATTRIBUTE);
        return value instanceof ProjectAuthContext ? (ProjectAuthContext) value : null;
    }

    public boolean isTemporaryToken() {
        return tokenId != null;
    }

    public Long getProjectId() { return projectId; }
    public String getProjectCode() { return projectCode; }
    public Long getAuthId() { return authId; }
    public String getAuthCode() { return authCode; }
    public String getAuthType() { return authType; }
    public Long getTokenId() { return tokenId; }
    public String getTokenCode() { return tokenCode; }
    public String getAuthPhase() { return authPhase; }
}
