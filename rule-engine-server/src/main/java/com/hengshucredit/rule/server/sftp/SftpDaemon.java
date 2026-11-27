package com.hengshucredit.rule.server.sftp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;

/** 单独的非 Web 进程，仅加载账户查询与 SFTP 监听器。 */
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class, org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class})
@Import({EngineSftpAccounts.class, EngineSftpProtection.class})
public class SftpDaemon {
    public static void run(String[] args) {
        new SpringApplicationBuilder(SftpDaemon.class).web(WebApplicationType.NONE)
                .properties("spring.main.keep-alive=true").run(args);
    }

    @Bean
    EngineSftpServer listener(EngineSftpAccounts accounts, EngineSftpProtection protection,
                              @Value("${engine.sftp.directory:./data/engine-sftp}") String directory,
                              @Value("${engine.sftp.bind-address:127.0.0.1}") String bind,
                              @Value("${engine.sftp.port:2222}") int port,
                              @Value("${engine.sftp.username:engine-storage}") String username,
                              @Value("${engine.sftp.password:}") String password) {
        return new EngineSftpServer(Path.of(directory), accounts, protection, bind, port, username, password);
    }
}
