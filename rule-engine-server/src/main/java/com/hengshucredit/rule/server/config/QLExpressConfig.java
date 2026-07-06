package com.hengshucredit.rule.server.config;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.QLExpressScriptSecurity;
import com.alibaba.qlexpress4.InitOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QLExpressConfig {

    @Bean
    public QLExpressEngine qlExpressEngine() {
        InitOptions options = InitOptions.builder()
                .traceExpression(true)
                .securityStrategy(QLExpressScriptSecurity.standardFunctionWhitelist())
                .build();
        return new QLExpressEngine(options);
    }
}
