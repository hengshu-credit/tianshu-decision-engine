package com.hengshucredit.rule.runtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** GitHub Pages/本地静态示例调用运行时；生产必须把来源收敛到实际文档域名。 */
@Configuration
public class RuntimeWebConfiguration implements WebMvcConfigurer {
    private final Environment environment;

    public RuntimeWebConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String configured = environment.getProperty("RUNTIME_CORS_ALLOWED_ORIGINS",
                "https://hengshu-credit.github.io,http://localhost:*,http://127.0.0.1:*");
        registry.addMapping("/api/**")
                .allowedOriginPatterns(configured.split(","))
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Auth-Code")
                .allowCredentials(false)
                .maxAge(600);
    }
}
