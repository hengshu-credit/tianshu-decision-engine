package com.hengshucredit.rule.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication(scanBasePackages = "com.hengshucredit.rule")
@EnableScheduling
@MapperScan("com.hengshucredit.rule.server.mapper")
public class RuleEngineApplication {
    public static void main(String[] args) {
        if (java.util.Arrays.asList(args).contains(com.hengshucredit.rule.server.consolelogin.ConsoleAdministratorRecovery.COMMAND)) {
            System.exit(com.hengshucredit.rule.server.consolelogin.ConsoleAdministratorRecovery.run());
            return;
        }
        SpringApplication.run(RuleEngineApplication.class, args);
    }
}
