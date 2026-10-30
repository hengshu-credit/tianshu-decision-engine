package com.hengshucredit.rule.server.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {
    @Bean
    public com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer credentialTypeHandlers(
            com.hengshucredit.rule.server.auth.StoredCredentialCodec codec,
            com.hengshucredit.rule.server.auth.CredentialStorageMigration migration) {
        return codec::register;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.hengshucredit.rule.server.security.ConsoleDataPermissionHandler consoleDataPermissionHandler;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.hengshucredit.rule.server.security.ConsoleDataWriteInterceptor consoleDataWriteInterceptor;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        if (consoleDataWriteInterceptor != null) interceptor.addInnerInterceptor(consoleDataWriteInterceptor);
        if (consoleDataPermissionHandler != null) interceptor.addInnerInterceptor(
                new com.hengshucredit.rule.server.security.ConsoleDataPermissionInterceptor(consoleDataPermissionHandler));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
