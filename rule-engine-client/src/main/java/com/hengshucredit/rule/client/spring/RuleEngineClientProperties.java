package com.hengshucredit.rule.client.spring;

import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import lombok.Data;

/**
 * 规则引擎客户端Spring配置属性
 * Redis配置完全由客户端服务的spring.redis.*提供，无需额外配置
 */
@Data
public class RuleEngineClientProperties {
    private String serverUrl;
    private String appName = "default";
    private String projectCode;

    /**
     * 访问Token，用于服务端身份认证
     */
    private String token;
    private String authType;
    private String username;
    private String password;
    private String apiKey;
    private String apiKeyPlacement = "HEADER";
    private String apiKeyParameterName = "X-Rule-Api-Key";
    private String accessKey;
    private String hmacSecret;
    private boolean tokenExchangeEnabled = true;
    private int tokenRefreshAheadSeconds = 60;

    private int l1CacheMaxSize = 1000;
    private int httpTimeoutMs = 3000;
    private String kafkaLogTopic = "rule-execution-log";
    /** 项目 ID，启动时自动从服务端同步函数定义（0 表示不同步函数） */
    private long projectId = 0;
    /** 是否开启表达式追踪，默认 true；关闭可提升执行性能 */
    private boolean traceEnabled = true;
    private boolean serverSideExecution = false;

    public ClientAuthConfig toAuthConfig() {
        ClientAuthConfig auth;
        if (ClientAuthConfig.BASIC.equalsIgnoreCase(authType)) {
            auth = ClientAuthConfig.basic(username, password);
        } else if (ClientAuthConfig.API_KEY.equalsIgnoreCase(authType)) {
            auth = ClientAuthConfig.apiKey(apiKeyParameterName, apiKey, apiKeyPlacement);
        } else if (ClientAuthConfig.HMAC_SHA256.equalsIgnoreCase(authType)) {
            auth = ClientAuthConfig.hmac(accessKey, hmacSecret);
        } else if (token != null && !token.isEmpty()) {
            auth = ClientAuthConfig.legacyToken(token);
        } else {
            return null;
        }
        if (!ClientAuthConfig.LEGACY_TOKEN.equals(auth.getAuthType())) {
            auth.setTokenExchangeEnabled(tokenExchangeEnabled);
        }
        auth.setRefreshAheadSeconds(tokenRefreshAheadSeconds);
        return auth;
    }
}
