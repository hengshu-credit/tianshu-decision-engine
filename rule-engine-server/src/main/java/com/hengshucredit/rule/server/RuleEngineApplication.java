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
        if (com.hengshucredit.rule.server.example.ExampleCommands.run(args)) return;
        if (java.util.Arrays.asList(args).contains(com.hengshucredit.rule.server.service.ModelFileMigration.COMMAND)) {
            var options = new java.util.ArrayList<>(java.util.Arrays.asList(args));
            options.removeIf(v -> v.equals(com.hengshucredit.rule.server.service.ModelFileMigration.COMMAND)
                    || v.startsWith("--server.port=") || v.startsWith("--server.address="));
            options.add("--server.port=0");
            options.add("--server.address=127.0.0.1");
            try (var context = SpringApplication.run(RuleEngineApplication.class, options.toArray(String[]::new))) {
                int changed = context.getBean(com.hengshucredit.rule.server.service.ModelFileMigration.class).migrate();
                System.out.println("模型文件迁移完成，已迁移记录数 " + changed);
            }
            return;
        }
        if (java.util.Arrays.asList(args).contains(com.hengshucredit.rule.server.consolelogin.ConsoleAdministratorRecovery.COMMAND)) {
            System.exit(com.hengshucredit.rule.server.consolelogin.ConsoleAdministratorRecovery.run());
            return;
        }
        if (java.util.Arrays.asList(args).contains(com.hengshucredit.rule.server.example.CreditExampleImport.COMMAND)) {
            try (var context = SpringApplication.run(RuleEngineApplication.class, "--server.port=0", "--server.address=127.0.0.1")) {
                new com.hengshucredit.rule.server.example.CreditExampleImport(context,
                        java.nio.file.Path.of(System.getenv().getOrDefault("EXAMPLE_CREDENTIALS", "/state/installation.json")))
                        .load(java.nio.file.Path.of(System.getenv().getOrDefault("EXAMPLE_SQL", "/init/data-tianshu-example.sql")));
            } catch (Exception error) {
                System.err.println("授信样例导入失败：" + error.getMessage());
                System.exit(1);
            }
            return;
        }
        SpringApplication.run(RuleEngineApplication.class, args);
    }
}
