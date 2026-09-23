package com.hengshucredit.rule.client.auth;

public class ProjectClientAuthenticationException extends RuntimeException {

    public ProjectClientAuthenticationException(String message) {
        super(message);
    }

    public ProjectClientAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
