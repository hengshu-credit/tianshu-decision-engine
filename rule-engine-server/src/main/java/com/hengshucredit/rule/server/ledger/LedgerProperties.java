package com.hengshucredit.rule.server.ledger;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rule-engine.ledger")
public class LedgerProperties {
    /** 仅部署配置可设置扫描服务地址；不能由上传请求指定。空地址保持待检。 */
    private String antivirusHost = "";
    private int antivirusPort = 3310;
    private int antivirusTimeoutMs = 10000;
}
