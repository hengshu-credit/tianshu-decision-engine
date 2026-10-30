package com.hengshucredit.rule.client.function;

import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RequestContext;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ClientFunctionMethodSignatureTest {
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
        JSONObject definition = function("[{\"name\":\"value\",\"type\":\"NUMBER\",\"javaType\":\"int\"}]");
        definition.put("implClass", StaticOnly.class.getName()); definition.put("implMethod", "triple");
        QLExpressEngine engine = new QLExpressEngine();
        RequestContext context = new RequestContext();
        try (var bound = context.bindFunctions(new ClientFunctionRegistrar(engine, null).prepareFrozen(List.of(definition)))) {
            var result = engine.execute(engine.prepare("return signature_probe(4);"), Map.of(), false, context);
            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(12, result.getResult());
        } catch (IllegalStateException error) { fail("Public static binding failed: " + error.getMessage()); }
    }

    @Test public void explicitSignatureIsHonoredInFrozenTargets() {
        assertEquals("double", execute("[{\"name\":\"value\",\"type\":\"DOUBLE\",\"javaType\":\"double\"}]"));
        assertEquals("int", execute("[{\"name\":\"value\",\"type\":\"NUMBER\",\"javaType\":\"int\"}]"));
    }

    @Test public void beanUsesTheSameExactSignatureContract() {
        try (var beans = new org.springframework.context.support.StaticApplicationContext()) {
            beans.getBeanFactory().registerSingleton("methods", new Overloads());
            QLExpressEngine engine = new QLExpressEngine(); RequestContext context = new RequestContext();
            JSONObject function = function("[{\"name\":\"value\",\"type\":\"NUMBER\",\"javaType\":\"int\"}]");
            function.put("implType", "BEAN"); function.put("implBeanName", "methods");
            try (var bound = context.bindFunctions(new ClientFunctionRegistrar(engine, beans).prepareFrozen(List.of(function)))) {
                var result = engine.execute(engine.prepare("return signature_probe(12);"), Map.of(), false, context);
                assertTrue(result.getErrorMessage(), result.isSuccess()); assertEquals("int", result.getResult());
            }
        }
    }

    @Test public void oldAndNewSignaturesWithSameFunctionIdentityStayIndependent() {
        QLExpressEngine engine = new QLExpressEngine();
        ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(engine, null);
        var old = registrar.prepareFrozen(List.of(function("[{\"name\":\"value\",\"type\":\"DOUBLE\"}]")));
        var next = registrar.prepareFrozen(List.of(function("[{\"name\":\"value\",\"type\":\"DOUBLE\",\"javaType\":\"double\"}]")));
        RequestContext context = new RequestContext();
        var script = engine.prepare("return signature_probe(12);");
        for (var bindings : List.of(old, next, old)) {
            try (var bound = context.bindFunctions(bindings)) {
                var result = engine.execute(script, Map.of(), false, context);
                assertTrue(result.getErrorMessage(), result.isSuccess());
                assertEquals(bindings == next ? "double" : "object", result.getResult());
            }
        }
    }

    @Test public void legacyNumberAndDoubleKeepTheirExistingBindings() {
        assertEquals("double", execute("[{\"name\":\"value\",\"type\":\"NUMBER\"}]"));
        assertEquals("object", execute("[{\"name\":\"value\",\"type\":\"DOUBLE\"}]"));
    }

    @Test public void malformedParamsCannotPrepareTheZeroArgumentOverload() {
        for (String params : List.of("not-json", "null", "[null]", "[42]", "{}")) {
            ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(new QLExpressEngine(), null);
            JSONObject definition = function(params); definition.put("implMethod", "unsafe");
            assertThrows("Invalid configuration must be rejected: " + params, IllegalStateException.class,
                    () -> registrar.prepareFrozen(List.of(definition)));
        }
    }

    @Test public void canonicalBuiltinReferencedByIdRemainsExecutableLocally() {
        JSONObject definition = function("[{\"name\":\"left\",\"type\":\"NUMBER\",\"javaType\":\"double\"},{\"name\":\"right\",\"type\":\"NUMBER\",\"javaType\":\"double\"}]");
        definition.put("funcCode", "numAdd"); definition.put("implClass", "com.hengshucredit.rule.core.function.DecisionBuiltinFunctions"); definition.put("implMethod", "numAdd");
        QLExpressEngine engine = new QLExpressEngine(); RequestContext context = new RequestContext();
        try (var bound = context.bindFunctions(new ClientFunctionRegistrar(engine, null).prepareFrozen(List.of(definition)))) {
            var result = engine.execute(engine.prepare("return invokeFunctionById(\"901\",12,3);"), Map.of(), false, context);
            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(15, ((Number) result.getResult()).intValue());
        } catch (IllegalStateException error) { fail("Canonical builtin rejected: " + error.getMessage()); }
    }

    @Test public void canonicalBuiltinHttpAndPushDoNotBreakPlatformForms() {
        JSONObject definition = function("[{\"name\":\"data\",\"type\":\"OBJECT\",\"javaType\":\"java.lang.Object\"}]");
        definition.put("funcCode", "max"); definition.put("implClass", "com.hengshucredit.rule.core.function.AggregateBuiltinFunctions"); definition.put("implMethod", "max"); definition.put("scope", "GLOBAL");
        QLExpressEngine engine = new QLExpressEngine(); ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(engine, null);
        registrar.replaceRemoteSnapshot(List.of(definition));
        assertTrue(registrar.hasRemoteFunction("GLOBAL", null, "max"));
        assertEquals(9, ((Number) engine.execute("max(1,9,3)", Map.of(), false).getResult()).intValue());
        assertTrue(registrar.tryRegisterRemoteFromPush("GLOBAL", null, "max", "JAVA", null,
                definition.getString("implClass"), "max", null, definition.getString("paramsJson")));
        RequestContext empty = new RequestContext();
        try (var bound = empty.bindFunctions(Map.of())) {
            assertEquals(9, ((Number) engine.execute(engine.prepare("max(1,9,3)"), Map.of(), false, empty).getResult()).intValue());
            assertFalse(engine.execute(engine.prepare("invokeFunctionById(\"901\",[1,9,3])"), Map.of(), false, empty).isSuccess());
        }
        RequestContext context = new RequestContext();
        try (var bound = context.bindFunctions(registrar.prepareFrozen(List.of(definition)))) {
            var result = engine.execute(engine.prepare("invokeFunctionById(\"901\", [1,9,3])"), Map.of(), false, context);
            assertTrue(result.getErrorMessage(), result.isSuccess()); assertEquals(9, ((Number) result.getResult()).intValue());
        }
    }

    private String execute(String params) {
        QLExpressEngine engine = new QLExpressEngine();
        ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(engine, null);
        RequestContext context = new RequestContext();
        try (var binding = context.bindFunctions(registrar.prepareFrozen(List.of(function(params))))) {
            var result = engine.execute(engine.prepare("return signature_probe(12);"), Map.of(), false, context);
            assertTrue(result.getErrorMessage(), result.isSuccess());
            return (String) result.getResult();
        }
    }

    private JSONObject function(String params) {
        JSONObject function = new JSONObject();
        function.put("id", 901L); function.put("funcCode", "signature_probe"); function.put("implType", "JAVA");
        function.put("implClass", Overloads.class.getName()); function.put("implMethod", "choose"); function.put("paramsJson", params);
        return function;
    }
}
