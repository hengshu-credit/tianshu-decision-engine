package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleFunction;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.*;

public class FunctionMethodCatalogServiceTest {
    @Test public void emptyImplementationSelectionReturnsAnExplicitInvalidState() {
        assertEquals("INVALID", new FunctionMethodCatalogService().inspect(new RuleFunction()).status());
    }
    private static int constructors;
    private static int calls;
    public static class Methods {
        public Methods() { constructors++; }
        @com.hengshucredit.rule.core.function.ScriptCallable public String choose(double value) { calls++; return "double"; }
        @com.hengshucredit.rule.core.function.ScriptCallable public String choose(Object value) { calls++; return "object"; }
    }

    @Test public void discoveryShowsOverloadsWithoutConstructingOrCallingThem() {
        constructors = 0; calls = 0;
        var result = new FunctionMethodCatalogService().inspect(function());
        assertTrue(result.valid());
        assertEquals("choose(double)", result.signature());
        assertEquals(2, result.methods().size());
        assertEquals(0, constructors); assertEquals(0, calls);
        assertTrue(result.methods().stream().noneMatch(method -> method.name().equals("wait")));
        assertTrue(result.methods().stream().anyMatch(method -> method.signature().equals("choose(java.lang.Object)")));
    }

    @Test public void existingLegacySignatureIsShownWithoutSilentlyChangingItsBinding() {
        RuleFunction function = function();
        function.setParamsJson("[{\"name\":\"value\",\"type\":\"DOUBLE\"}]");
        var result = new FunctionMethodCatalogService().inspect(function);
        assertTrue(result.valid()); assertTrue(result.legacySignature());
        assertEquals("choose(java.lang.Object)", result.signature());
        assertFalse(function.getParamsJson().contains("javaType"));
    }

    @Test public void invalidSignatureStillAllowsChoosingARealMethod() {
        RuleFunction function = function(); function.setParamsJson("not-json");
        var result = new FunctionMethodCatalogService().inspect(function);
        assertEquals("AVAILABLE", result.status()); assertFalse(result.valid());
        assertEquals(2, result.methods().size()); assertNull(result.signature());
        assertTrue(result.message().contains("JSON"));
    }

    @Test public void absentTargetIsExplicitlyUnverifiedInsteadOfPretendingThereAreNoMethods() {
        RuleFunction function = function(); function.setImplClass("sdk.only.BusinessFunctions");
        var result = new FunctionMethodCatalogService().inspect(function);
        assertEquals("UNAVAILABLE", result.status()); assertFalse(result.valid());
        assertTrue(result.message().contains("尚未验证"));
    }

    @Test public void beanDiscoveryUsesItsTypeWithoutCallingTheBean() {
        try (var context = new StaticApplicationContext()) {
            context.registerSingleton("methods", Methods.class);
            FunctionMethodCatalogService service = new FunctionMethodCatalogService();
            ReflectionTestUtils.setField(service, "applicationContext", context);
            constructors = 0; calls = 0;
            RuleFunction function = function(); function.setImplType("BEAN"); function.setImplBeanName("methods");
            var result = service.inspect(function);
            assertTrue(result.valid()); assertEquals("choose(double)", result.signature());
            assertEquals(0, constructors); assertEquals(0, calls);
        }
    }

    private RuleFunction function() {
        RuleFunction function = new RuleFunction(); function.setFuncCode("probe"); function.setImplType("JAVA");
        function.setImplClass(Methods.class.getName()); function.setImplMethod("choose");
        function.setParamsJson("[{\"name\":\"value\",\"type\":\"DOUBLE\",\"javaType\":\"double\"}]");
        return function;
    }
}
