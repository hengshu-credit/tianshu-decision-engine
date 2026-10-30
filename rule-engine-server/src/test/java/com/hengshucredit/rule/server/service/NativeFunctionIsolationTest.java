package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.entity.RuleFunction;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.Assert.*;

public class NativeFunctionIsolationTest {
    private static int constructed;
    public static class Unapproved {
        public Unapproved() { constructed++; }
        public String read() { throw new AssertionError("Unapproved method was executed"); }
    }

    @Test public void classAndBeanBindingRejectBeforeConstructingUnapprovedCode() {
        constructed = 0;
        RuleFunction function = new RuleFunction(); function.setId(1L); function.setFuncCode("readHost");
        function.setImplType("JAVA"); function.setImplClass(Unapproved.class.getName()); function.setImplMethod("read");
        var registrar = new FunctionRegistrar();
        assertThrows(IllegalStateException.class, () -> registrar.prepareFunctions(List.of(function), new QLExpressEngine().getRunner()));
        assertEquals(0, constructed);
        try (var beans = new StaticApplicationContext()) {
            beans.registerSingleton("unapproved", Unapproved.class);
            ReflectionTestUtils.setField(registrar, "applicationContext", beans);
            function.setImplType("BEAN"); function.setImplBeanName("unapproved");
            assertThrows(IllegalStateException.class, () -> registrar.prepareFunctions(List.of(function), new QLExpressEngine().getRunner()));
            assertEquals(0, constructed);
        }
    }

    @Test public void externalRequestAndResponseScriptsCannotReadDeploymentState() {
        var scripts = new ExternalApiScriptService();
        for (String source : List.of("System.getenv()", "System.getProperties()", "new java.io.File('.env.server')",
                "Runtime.getRuntime()", "Class.forName('java.lang.System')", "new ProcessBuilder('whoami')",
                "'test'.getClass().getClassLoader()")) {
            assertThrows(source, IllegalArgumentException.class, () -> scripts.executeRequest(source, java.util.Map.of()));
            assertThrows(source, IllegalArgumentException.class, () -> scripts.executeResponse(source, java.util.Map.of()));
        }
    }
}
