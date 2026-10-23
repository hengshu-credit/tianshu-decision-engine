package com.hengshucredit.rule.client.auth;

public class ProjectClientAuthenticationException extends com.hengshucredit.rule.model.PlatformException {

    public ProjectClientAuthenticationException(com.hengshucredit.rule.model.PlatformCode code, String detail) {
        super(code, detail);
    }

    public ProjectClientAuthenticationException(com.hengshucredit.rule.model.PlatformCode code, String detail, Throwable cause) {
        super(code, detail, cause);
    }

    public ProjectClientAuthenticationException(String message) {
        super(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, message);
    }

    public ProjectClientAuthenticationException(String message, Throwable cause) {
        super(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, message, cause);
    }
}
