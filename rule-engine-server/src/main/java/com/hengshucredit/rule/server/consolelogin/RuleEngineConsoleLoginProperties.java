package com.hengshucredit.rule.server.consolelogin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 控制台（管理端）用户名密码登录相关配置，由 application.yml 中 rule-engine.console-login 绑定。
 * 账户和密码仅存于数据库，首次使用注册页面初始化；恢复码由服务器维护命令签发。
 */
@Data
@ConfigurationProperties(prefix = "rule-engine.console-login")
public class RuleEngineConsoleLoginProperties {

    /**
     * 是否启用管理端会话登录；关闭时行为与改造前一致，所有 /api/rule/** 接口不要求登录。
     */
    private boolean enabled = false;

    /** 是否允许通过登录页注册控制台账户。 */
    private boolean registrationEnabled = false;

    /** 是否允许通过邮箱找回控制台密码。 */
    private boolean passwordRecoveryEnabled = true;

    /** 邮件链接和令牌相关配置。 */
    private Mail mail = new Mail();

    /**
     * 需要会话校验的路径（Ant 风格）；默认覆盖管理端 API 前缀。
     */
    private List<String> includePatterns = new ArrayList<>(Arrays.asList("/api/rule/**"));

    /**
     * 不参与会话校验的路径（Ant 风格）；如同步接口、日志上报等需被外部系统匿名调用。
     */
    private List<String> excludePatterns = new ArrayList<>(Arrays.asList(
            "/api/auth/**",
            "/api/rule/auth/token",
            "/api/rule/sync/**",
            "/api/rule/runtime/**",
            "/api/rule/openapi/**",
            "/api/rule/log/report"
    ));

    /**
     * 登录成功后写入 HttpSession 的属性名。
     */
    private String sessionUsernameAttribute = "RULE_CONSOLE_USERNAME";
    private String sessionOperatorNameAttribute = "RULE_CONSOLE_OPERATOR_NAME";

    /**
     * 登录成功后写入 HttpSession 的本地账户 ID 属性名。
     */
    private String sessionUserIdAttribute = "RULE_CONSOLE_USER_ID";

    /**
     * 登录成功时记录的权限版本属性名，权限调整后用于使旧会话失效。
     */
    private String sessionPermissionVersionAttribute = "RULE_CONSOLE_PERMISSION_VERSION";

    @Data
    public static class Mail {
        /** 发件人地址；为空时邮件功能视为未配置。 */
        private String from = "";

        /** 前端控制台绝对地址，例如 https://console.example.com。 */
        private String baseUrl = "";

        private int verificationTtlMinutes = 30;
        private int passwordResetTtlMinutes = 30;
    }
}
