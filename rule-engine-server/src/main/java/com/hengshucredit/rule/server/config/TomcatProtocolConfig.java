package com.hengshucredit.rule.server.config;

import org.apache.coyote.http11.Http11Nio2Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * 使用 NIO2，避免受限 Windows 环境中 JDK Selector 初始化依赖回环管道而启动失败。
 */
@Configuration
public class TomcatProtocolConfig
        implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.setProtocol(Http11Nio2Protocol.class.getName());
    }
}
