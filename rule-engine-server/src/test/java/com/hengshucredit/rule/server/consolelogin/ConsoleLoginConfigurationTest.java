package com.hengshucredit.rule.server.consolelogin;

import com.hengshucredit.rule.server.mapper.ConsolePermissionMapper;
import com.hengshucredit.rule.server.mapper.ConsoleRoleMapper;
import com.hengshucredit.rule.server.mapper.ConsoleRolePermissionMapper;
import com.hengshucredit.rule.server.mapper.ConsoleSecurityAuditLogMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserPermissionOverrideMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserRoleMapper;
import com.hengshucredit.rule.server.security.ConsolePermissionService;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertNotNull;

public class ConsoleLoginConfigurationTest {

    @Test
    public void enabledConfigurationStartsWithoutCircularDependency() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context, "rule-engine.console-login.enabled=true");
            context.registerBean(RuleEngineConsoleLoginProperties.class);
            context.register(ConsoleLoginConfiguration.class);
            context.registerBean(DatabaseConsoleAccountService.class);
            registerAccountDependencies(context);

            context.refresh();

            assertNotNull(context.getBean(ConsoleSessionAuthInterceptor.class));
            assertNotNull(context.getBean(DatabaseConsoleAccountService.class));
        }
    }

    private static void registerAccountDependencies(
            AnnotationConfigApplicationContext context) {
        context.registerBean(com.hengshucredit.rule.server.mapper.ConsoleUserProjectMapper.class,
                () -> noOpProxy(com.hengshucredit.rule.server.mapper.ConsoleUserProjectMapper.class));
        context.registerBean(com.hengshucredit.rule.server.mapper.ConsoleUserProjectPermissionMapper.class,
                () -> noOpProxy(com.hengshucredit.rule.server.mapper.ConsoleUserProjectPermissionMapper.class));
        context.registerBean(ConsoleUserMapper.class,
                () -> noOpProxy(ConsoleUserMapper.class));
        context.registerBean(ConsoleRoleMapper.class,
                () -> noOpProxy(ConsoleRoleMapper.class));
        context.registerBean(ConsolePermissionMapper.class,
                () -> noOpProxy(ConsolePermissionMapper.class));
        context.registerBean(ConsoleUserRoleMapper.class,
                () -> noOpProxy(ConsoleUserRoleMapper.class));
        context.registerBean(ConsoleRolePermissionMapper.class,
                () -> noOpProxy(ConsoleRolePermissionMapper.class));
        context.registerBean(ConsoleSecurityAuditLogMapper.class,
                () -> noOpProxy(ConsoleSecurityAuditLogMapper.class));
        context.registerBean(ConsoleUserPermissionOverrideMapper.class,
                () -> noOpProxy(ConsoleUserPermissionOverrideMapper.class));
        context.registerBean(ConsolePermissionService.class,
                ConsolePermissionService::new);
    }

    private static <T> T noOpProxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    Class<?> returnType = method.getReturnType();
                    if (!returnType.isPrimitive()) return null;
                    if (returnType == boolean.class) return false;
                    if (returnType == char.class) return '\0';
                    if (returnType == byte.class) return (byte) 0;
                    if (returnType == short.class) return (short) 0;
                    if (returnType == int.class) return 0;
                    if (returnType == long.class) return 0L;
                    if (returnType == float.class) return 0F;
                    return 0D;
                }));
    }
}
