package com.hengshucredit.rule.server.config;

import com.hengshucredit.rule.server.governance.GovernedProjectionMutationInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private TokenAuthInterceptor tokenAuthInterceptor;

    @Autowired
    private com.hengshucredit.rule.server.audit.ConsoleOperationAudit consoleOperationAudit;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenAuthInterceptor)
                .addPathPatterns("/api/**");
        registry.addInterceptor(new GovernedProjectionMutationInterceptor())
                .addPathPatterns("/api/rule/**");
        registry.addInterceptor(consoleOperationAudit)
                .addPathPatterns("/api/rule/**").order(100);
    }
}
