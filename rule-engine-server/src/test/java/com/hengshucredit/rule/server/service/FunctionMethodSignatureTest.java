package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RequestContext;
import com.hengshucredit.rule.model.entity.RuleFunction;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class FunctionMethodSignatureTest {
    public static class Overloads {
        @com.hengshucredit.rule.core.function.ScriptCallable public String choose(double value) { return "double"; }
        @com.hengshucredit.rule.core.function.ScriptCallable public String choose(int value) { return "int"; }
        @com.hengshucredit.rule.core.function.ScriptCallable public String choose(Object value) { return "object"; }
        @com.hengshucredit.rule.core.function.ScriptCallable public String unsafe() { return "wrong-zero-argument-method"; }
        @com.hengshucredit.rule.core.function.ScriptCallable public String unsafe(Object value) { return "one-argument-method"; }
    }

    public static class StaticOnly {
        private StaticOnly() { throw new AssertionError("Static method must not construct its declaring class"); }
        @com.hengshucredit.rule.core.function.ScriptCallable public static int triple(int value) { return value * 3; }
    }

    @Test public void publicStaticMethodDoesNotRequireAConstructor() {
        RuleFunction function = function("[{\"name\":\"value\",\"type\":\"NUMBER\",\"javaType\":\"int\"}]");
        function.setImplClass(StaticOnly.class.getName()); function.setImplMethod("triple");
        QLExpressEngine engine = new QLExpressEngine();
        RequestContext context = new RequestContext();
        try (var bound = context.bindFunctions(new FunctionRegistrar().prepareFunctions(List.of(function), engine.getRunner()))) {
            var result = engine.execute(engine.prepare("return signature_probe(4);"), Map.of(), false, context);
            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(12, result.getResult());
        } catch (IllegalStateException error) { fail("Public static binding failed: " + error.getMessage()); }
    }

    @Test public void explicitJvmTypeSelectsDoubleInsteadOfObject() {
        assertEquals("double", execute("[{\"name\":\"value\",\"type\":\"DOUBLE\",\"javaType\":\"double\"}]"));
    }

    @Test public void beanUsesTheSameExactSignatureContract() {
        try (var beans = new org.springframework.context.support.StaticApplicationContext()) {
            beans.getBeanFactory().registerSingleton("methods", new Overloads());
            FunctionRegistrar registrar = new FunctionRegistrar();
            org.springframework.test.util.ReflectionTestUtils.setField(registrar, "applicationContext", beans);
            RuleFunction function = function("[{\"name\":\"value\",\"type\":\"NUMBER\",\"javaType\":\"int\"}]");
            function.setImplType("BEAN"); function.setImplBeanName("methods");
            QLExpressEngine engine = new QLExpressEngine(); RequestContext context = new RequestContext();
            try (var bound = context.bindFunctions(registrar.prepareFunctions(List.of(function), engine.getRunner()))) {
                var result = engine.execute(engine.prepare("return signature_probe(12);"), Map.of(), false, context);
                assertTrue(result.getErrorMessage(), result.isSuccess()); assertEquals("int", result.getResult());
            }
        }
    }

    @Test public void explicitJvmTypeDistinguishesIntFromNumberDefault() {
        assertEquals("int", execute("[{\"name\":\"value\",\"type\":\"NUMBER\",\"javaType\":\"int\"}]"));
    }

    @Test public void legacyDoubleKeepsItsPreviouslyBoundObjectOverload() {
        assertEquals("object", execute("[{\"name\":\"value\",\"type\":\"DOUBLE\"}]"));
    }

    @Test public void legacyNumberKeepsDoubleAndExplicitObjectRemainsExact() {
        assertEquals("double", execute("[{\"name\":\"value\",\"type\":\"NUMBER\"}]"));
        assertEquals("object", execute("[{\"name\":\"value\",\"type\":\"NUMBER\",\"javaType\":\"java.lang.Object\"}]"));
    }

    @Test public void malformedParamsNeverBecomeZeroArguments() {
        for (String params : List.of("not-json", "null", "[null]", "[42]", "{}")) {
            FunctionRegistrar registrar = new FunctionRegistrar();
            RuleFunction function = function(params);
            function.setImplMethod("unsafe");
            assertThrows("Invalid configuration must be rejected: " + params, IllegalStateException.class,
                    () -> registrar.prepareFunctions(List.of(function), new QLExpressEngine().getRunner()));
        }
    }

    @Test public void builtinNameCannotBypassAnInvalidDeclaredSignature() {
        RuleFunction function = function("[{\"name\":\"left\",\"type\":\"NUMBER\",\"javaType\":\"int\"},{\"name\":\"right\",\"type\":\"NUMBER\",\"javaType\":\"int\"}]");
        function.setFuncCode("numAdd"); function.setImplClass("com.hengshucredit.rule.core.function.DecisionBuiltinFunctions"); function.setImplMethod("numAdd");
        assertThrows(IllegalStateException.class, () -> new FunctionRegistrar().prepareFunctions(List.of(function), new QLExpressEngine().getRunner()));
        function.setParamsJson("not-json");
        assertThrows(IllegalStateException.class, () -> new FunctionRegistrar().prepareFunctions(List.of(function), new QLExpressEngine().getRunner()));
    }

    private String execute(String params) {
        QLExpressEngine engine = new QLExpressEngine();
        RequestContext context = new RequestContext();
        try (var binding = context.bindFunctions(new FunctionRegistrar().prepareFunctions(List.of(function(params)), engine.getRunner()))) {
            var result = engine.execute(engine.prepare("return signature_probe(12);"), Map.of(), false, context);
            assertTrue(result.getErrorMessage(), result.isSuccess());
            return (String) result.getResult();
        }
    }

    private RuleFunction function(String params) {
        RuleFunction function = new RuleFunction();
        function.setId(901L); function.setFuncCode("signature_probe"); function.setImplType("JAVA");
        function.setImplClass(Overloads.class.getName()); function.setImplMethod("choose"); function.setParamsJson(params);
        return function;
    }
}
