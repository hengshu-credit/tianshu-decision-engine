package com.hengshucredit.rule.server.auth;

import java.io.IOException;

public class RequestBodyTooLargeException extends IOException {

    public RequestBodyTooLargeException() {
        super("Protected request body exceeds the configured limit");
    }
}
