package com.hengshucredit.rule.server.consolelogin;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

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

            context.refresh();

            assertNotNull(context.getBean(ConsoleSessionAuthInterceptor.class));
            assertNotNull(context.getBean(ConsoleLoginAuthenticator.class));
        }
    }
}
