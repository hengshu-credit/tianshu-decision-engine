package com.hengshucredit.rule.server.security;

import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.service.SourceResolutionExecutor;
import com.hengshucredit.rule.server.service.ModelExecutionTimeoutExecutor;
import com.hengshucredit.rule.server.service.OpenRuleExecutionExecutor;
import com.hengshucredit.rule.server.service.RequestDeadlineContext;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class ConsoleAsyncScopeTest {
    @Test public void workerTasksKeepConsoleScopeAndDoNotLeakItToNextTask() throws Exception {
        var access = new ConsoleProjectAccess();
        var properties = new RuleEngineConsoleLoginProperties(); properties.setEnabled(true);
        ReflectionTestUtils.setField(access, "properties", properties);
        ReflectionTestUtils.setField(access, "permissions", new ConsolePermissionService() {
            @Override public ConsoleProjectAccess.Scope scope(Long id) { return new ConsoleProjectAccess.Scope(id, Set.of(1L), Set.of("field:view", "log:view")); }
        });
        var request = new MockHttpServletRequest("POST", "/api/rule/variable/11/test");
        request.getSession().setAttribute(properties.getSessionUserIdAttribute(), 7L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestDeadlineContext.clear();
        var models = new ModelExecutionTimeoutExecutor(1, 4);
        var rules = new OpenRuleExecutionExecutor(1, 4);
        try (var sources = new SourceResolutionExecutor(2)) {
            // 无包装的工作线程没有 ServletRequest；对照证明不能依赖 HTTP ThreadLocal 自动传播。
            assertNull(sources.submit(access::current).get(5, TimeUnit.SECONDS));
            ReflectionTestUtils.setField(sources, "consoleProjects", access);
            ReflectionTestUtils.setField(models, "consoleProjects", access);
            ReflectionTestUtils.setField(rules, "consoleProjects", access);
            assertEquals(Set.of(1L), sources.submit(access::current).get(5, TimeUnit.SECONDS).projectIds());
            assertEquals(Set.of(1L), models.execute(access::current, 5000).projectIds());
            assertEquals(Set.of(1L), rules.execute(access::current).projectIds());
            var policy = new ConsoleDataPermissionHandler(access);
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> sources.submit(() -> { policy.requireHistoryScope("GLOBAL", null); return true; }).get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof ConsoleAccessDeniedException);
            RequestContextHolder.resetRequestAttributes();
            assertNull(models.execute(access::current, 5000));
            assertNull(rules.execute(access::current));
            assertNull(sources.submit(access::current).get(5, TimeUnit.SECONDS));
        } finally { models.close(); rules.close(); RequestContextHolder.resetRequestAttributes(); RequestDeadlineContext.clear(); }
    }
}
