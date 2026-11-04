package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.controller.mgmt.DashboardController;
import com.hengshucredit.rule.server.security.*;
import com.hengshucredit.rule.server.system.SystemSettingsAccess;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class DashboardDefaultsAccessTest {
    @Test
    public void sharedMetadataReadDoesNotGrantFieldOrWritePermissions() {
        var handler = new ConsoleDataPermissionHandler(null);
        var scope = new ConsoleProjectAccess.Scope(8L, Set.of(), Set.of("rule:view"));
        assertEquals(null, handler.predicate(scope, "rule_dashboard_default", "d", 0));
        assertEquals("1 = 0", handler.forMutation(() -> handler.predicate(scope, "rule_dashboard_default", "d", 0)));
        assertFalse(scope.globalCan("field:view"));
        assertFalse(scope.globalCan("system:edit"));
    }
    @Test
    public void onlyRealConsoleAdministratorsWithGlobalWriteAccessCanChangeDefaults() throws Exception {
        for (int mode = 0; mode < 4; mode++) {
            var login = new RuleEngineConsoleLoginProperties();
            login.setEnabled(mode != 3);
            boolean administrator = mode != 1;
            boolean globalEditable = mode != 2;
            var permissions = new ConsolePermissionService() {
                @Override public ConsoleProjectAccess.Scope scope(Long id) {
                    return new ConsoleProjectAccess.Scope(id, Set.of(9L),
                            administrator ? Set.of("system:edit") : Set.of("rule:view"), true, globalEditable,
                            Map.of(9L, Map.of("system:edit", "ALLOW")));
                }
            };
            AtomicInteger writes = new AtomicInteger();
            var mappings = new DashboardMappingService(null, null) {
                @Override public DashboardResponses.Settings saveSystemDefaults(String actor, Long version,
                        Map<DashboardMetricField, DashboardMapping.StoredMapping> values) {
                    assertEquals("operator", actor);
                    writes.incrementAndGet();
                    return new DashboardResponses.Settings(true, null, false, List.of(), List.of(), 1L);
                }
            };
            var controller = new DashboardController(new DashboardAccessService(login, permissions), null, mappings, null, null, null);
            ReflectionTestUtils.setField(controller, "systemSettingsAccess", new SystemSettingsAccess(permissions, login));
            var request = new MockHttpServletRequest("PUT", "/api/rule/dashboard/settings/defaults");
            request.getSession().setAttribute(login.getSessionUserIdAttribute(), 2L);
            request.getSession().setAttribute(login.getSessionUsernameAttribute(), "operator");
            var body = new DashboardController.SystemSettingsUpdateRequest(0L, Map.of());
            if (mode == 0) {
                assertEquals("000000", controller.saveSystemSettings(body, request).getCode());
                assertEquals(1, writes.get());
            } else {
                assertThrows(ConsoleAccessDeniedException.class, () -> controller.saveSystemSettings(body, request));
                assertEquals(0, writes.get());
            }
            if (mode == 1 || mode == 2) {
                var handler = new HandlerMethod(controller, DashboardController.class.getMethod("saveSystemSettings",
                        DashboardController.SystemSettingsUpdateRequest.class, jakarta.servlet.http.HttpServletRequest.class));
                assertFalse(new ConsolePermissionInterceptor(login, permissions).preHandle(request, new MockHttpServletResponse(), handler));
            }
        }
    }
}
