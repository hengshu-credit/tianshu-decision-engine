package com.hengshucredit.rule.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 规则引擎客户端集成示例
 *
 * 演示业务系统通过 rule-engine-client-http 调用服务端已发布规则。
 *
 * 启动前提：
 * 1. rule-engine-server 已启动并监听 8080 端口
 * 2. 配置项目调用凭据和允许调用的规则编码
 * 3. 在 Server 端创建项目、关联全局规则或设计项目规则并发布
 */
@SpringBootApplication(scanBasePackages = {"com.hengshucredit.rule.example"})
public class RuleExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuleExampleApplication.class, args);
    }
}
