package com.hengshucredit.rule.server.auth;

public final class ProjectAuthType {
    public static final String LEGACY_TOKEN = "LEGACY_TOKEN";
    public static final String API_KEY = "API_KEY";
    public static final String BASIC = "BASIC";
    public static final String HMAC_SHA256 = "HMAC_SHA256";
    public static final String BEARER_TOKEN = "BEARER_TOKEN";

    private ProjectAuthType() {
    }
}
