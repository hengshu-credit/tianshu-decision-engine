package com.hengshucredit.rule.server.security;

import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.service.FunctionMethodCatalogService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class ConsoleNativeFunctionPermissionTest {
    @Test public void projectFunctionEditorCannotConfigureArbitraryServerNativeCode() {
        var service = new FunctionMethodCatalogService();
        ReflectionTestUtils.setField(service, "consoleProjects", new ConsoleProjectAccess() {
            @Override public Scope current() { return new Scope(7L, Set.of(1L), Set.of("function:view", "function:edit")); }
        });
        assertThrows(ConsoleAccessDeniedException.class, () -> service.inspect(nativeFunction()));
    }
    @Test public void trustedGlobalNativePermissionAllowsExplicitNativeBinding() {
        var service = new FunctionMethodCatalogService();
        ReflectionTestUtils.setField(service, "consoleProjects", new ConsoleProjectAccess() {
            @Override public Scope current() { return new Scope(1L, Set.of(), Set.of("function:view", "function:edit", "function:native", "account:manage", "role:manage"), true, true, Map.of()); }
        });
        assertTrue(service.inspect(nativeFunction()).valid());
    }
    private RuleFunction nativeFunction() {
        var function = new RuleFunction(); function.setFuncCode("nativeEnvironment"); function.setImplType("JAVA");
        function.setImplClass("java.lang.System"); function.setImplMethod("getenv");
        function.setParamsJson("[{\"name\":\"key\",\"type\":\"STRING\",\"javaType\":\"java.lang.String\"}]");
        return function;
    }
}
