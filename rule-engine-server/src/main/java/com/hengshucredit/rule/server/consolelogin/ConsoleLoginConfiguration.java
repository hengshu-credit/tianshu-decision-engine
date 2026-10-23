package com.hengshucredit.rule.server.consolelogin;

import com.hengshucredit.rule.server.security.ConsolePermissionInterceptor;
import com.hengshucredit.rule.server.security.ConsolePermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 启用 rule-engine.console-login.enabled=true 时注册持久化账户的会话拦截器。
 */
@Configuration
@ConditionalOnProperty(prefix = "rule-engine.console-login", name = "enabled", havingValue = "true")
public class ConsoleLoginConfiguration implements WebMvcConfigurer {

    private final ConsoleSessionAuthInterceptor consoleSessionAuthInterceptor;
    private final RuleEngineConsoleLoginProperties ruleEngineConsoleLoginProperties;

    @Autowired(required = false)
    private ConsolePermissionService consolePermissionService;
    @Autowired(required = false)
    private com.hengshucredit.rule.server.security.ConsoleTargetAuthorization consoleTargetAuthorization;

    @Autowired(required = false)
    void setDatabaseAccountService(
            DatabaseConsoleAccountService databaseAccountService) {
        consoleSessionAuthInterceptor.setDatabaseAccountService(
                databaseAccountService);
    }

    public ConsoleLoginConfiguration(RuleEngineConsoleLoginProperties ruleEngineConsoleLoginProperties) {
        this.ruleEngineConsoleLoginProperties = ruleEngineConsoleLoginProperties;
        this.consoleSessionAuthInterceptor = new ConsoleSessionAuthInterceptor(ruleEngineConsoleLoginProperties);
    }

    /**
     * 会话校验拦截器 Bean。
     */
    @Bean
    public ConsoleSessionAuthInterceptor consoleSessionAuthInterceptor() {
        return consoleSessionAuthInterceptor;
    }

    /**
     * 将控制台登录拦截器挂到 MVC；与 {@link com.hengshucredit.rule.server.config.WebMvcConfig} 中全局拦截器共存。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(consoleSessionAuthInterceptor)
                .addPathPatterns(ruleEngineConsoleLoginProperties.getIncludePatterns())
                .excludePathPatterns(ruleEngineConsoleLoginProperties.getExcludePatterns())
                .excludePathPatterns("/api/external-callback/*");
        registry.addInterceptor(consoleSessionAuthInterceptor)
                .addPathPatterns("/api/auth/console/me", "/api/auth/console/profile",
                        "/api/auth/console/profile/**", "/api/auth/console/password/change",
                        "/api/auth/console/preferences/**");
        if (consolePermissionService != null) {
            registry.addInterceptor(new ConsolePermissionInterceptor(
                            ruleEngineConsoleLoginProperties, consolePermissionService))
                    .addPathPatterns(ruleEngineConsoleLoginProperties.getIncludePatterns())
                    .excludePathPatterns(ruleEngineConsoleLoginProperties.getExcludePatterns())
                    .excludePathPatterns("/api/external-callback/*");
        }
        if (consoleTargetAuthorization != null) registry.addInterceptor(consoleTargetAuthorization)
                .addPathPatterns(ruleEngineConsoleLoginProperties.getIncludePatterns())
                .excludePathPatterns(ruleEngineConsoleLoginProperties.getExcludePatterns())
                .excludePathPatterns("/api/external-callback/*");
    }
}
