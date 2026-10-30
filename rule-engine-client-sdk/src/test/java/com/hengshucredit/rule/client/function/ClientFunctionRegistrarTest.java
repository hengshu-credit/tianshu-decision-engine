package com.hengshucredit.rule.client.function;

import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientFunctionRegistrarTest {
    @Test public void absentLocalBeanDoesNotBlockOtherRemoteFunctionsAndCanRecoverWhenBeanAppears() {
        var context = new org.springframework.context.support.GenericApplicationContext();
        context.refresh();
        try {
            QLExpressEngine engine = new QLExpressEngine();
            var registrar = new ClientFunctionRegistrar(engine, context, "project-a");
            JSONObject bean = function("deferredService", "");
            bean.put("implType", "BEAN"); bean.put("implBeanName", "lateBean");
            bean.put("implMethod", "first"); bean.put("paramsJson", "[]");
            registrar.replaceRemoteSnapshot(Arrays.asList(bean, function("readyScore", "42")));
            assertResult(engine, "readyScore()", 42);
            var unavailable = engine.execute("deferredService()", Collections.emptyMap(), false);
            assertFalse(unavailable.isSuccess());
            assertTrue(unavailable.getErrorMessage(), unavailable.getErrorMessage().contains("lateBean"));
            context.getBeanFactory().registerSingleton("lateBean", new FrozenFunctions());
            assertResult(engine, "deferredService()", 1);
        } finally { context.close(); }
    }
    @Test public void frozenTargetsUseCompleteDefinitionsAndBoundedCacheWithoutRetiringActiveBindings() throws Exception {
        QLExpressEngine engine = new QLExpressEngine();
        ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(engine, null, "project-a");
        var old = registrar.prepareFrozen(Collections.singletonList(nativeFunction(1L, "first")));
        var same = registrar.prepareFrozen(Collections.singletonList(nativeFunction(1L, "first")));
        var newer = registrar.prepareFrozen(Collections.singletonList(nativeFunction(1L, "second")));
        org.junit.Assert.assertSame("热快照复用已准备目标，不重复反射", old.get("score"), same.get("score"));
        org.junit.Assert.assertNotSame("同ID/code但方法不同必须各自绑定", old.get("score"), newer.get("score"));
        for (long id = 2; id <= 1100; id++) registrar.prepareFrozen(Collections.singletonList(nativeFunction(id, "first")));
        var field = ClientFunctionRegistrar.class.getDeclaredField("frozenTargets"); field.setAccessible(true);
        assertTrue("冷版本目标缓存必须有界", ((java.util.Map<?, ?>) field.get(registrar)).size() <= 1024);
        org.junit.Assert.assertNotSame("超过预算的冷条目已被回收", old.get("score"), registrar.prepareFrozen(Collections.singletonList(nativeFunction(1L, "first"))).get("score"));
        var request = new com.hengshucredit.rule.core.engine.RequestContext();
        try (var installed = com.hengshucredit.rule.core.engine.RuntimeContextBridge.install(request)) {
            try (var bound = request.bindFunctions(old)) { assertResult(engine, "score()", 1); }
            try (var bound = request.bindFunctions(newer)) { assertResult(engine, "score()", 2); }
        }
    }

    @Test public void frozenBeanWithoutLocalContainerFailsExplicitly() {
        ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(new QLExpressEngine(), null, "project-a");
        JSONObject function = nativeFunction(1L, "first");
        function.put("implType", "BEAN"); function.put("implBeanName", "missingBean");
        org.junit.Assert.assertThrows(IllegalStateException.class, () -> registrar.prepareFrozen(Collections.singletonList(function)));
    }

    private static JSONObject nativeFunction(Long id, String method) {
        JSONObject function = new JSONObject(); function.put("id", id); function.put("funcCode", "score");
        function.put("implType", "JAVA"); function.put("implClass", FrozenFunctions.class.getName());
        function.put("implMethod", method); function.put("paramsJson", "[]");
        return function;
    }

    public static final class FrozenFunctions {
        @com.hengshucredit.rule.core.function.ScriptCallable public int first() { return 1; }
        @com.hengshucredit.rule.core.function.ScriptCallable public int second() { return 2; }
    }


    @Test
    public void projectFunctionOverridesGlobalThenDeletionFallsBackAndReadditionWins() {
        QLExpressEngine engine = new QLExpressEngine();
        ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(engine, null, "project-a");

        registrar.registerRemoteFromPush("GLOBAL", null, "score", "SCRIPT", "1",
                null, null, null, null);
        registrar.registerRemoteFromPush("PROJECT", "project-a", "score", "SCRIPT", "2",
                null, null, null, null);
        assertResult(engine, "score()", 2);

        registrar.removeRemote("PROJECT", "project-a", "score");
        assertResult(engine, "score()", 1);

        registrar.registerRemoteFromPush("PROJECT", "project-a", "score", "SCRIPT", "3",
                null, null, null, null);
        assertResult(engine, "score()", 3);

        registrar.removeRemote("GLOBAL", null, "score");
        assertResult(engine, "score()", 3);
    }

    @Test
    public void globalDeletionDoesNotRemoveProjectFunction() {
        QLExpressEngine engine = new QLExpressEngine();
        ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(engine, null, "project-a");
        registrar.registerRemoteFromPush("GLOBAL", null, "score", "SCRIPT", "1",
                null, null, null, null);
        registrar.registerRemoteFromPush("PROJECT", "project-a", "score", "SCRIPT", "2",
                null, null, null, null);

        registrar.removeRemote("GLOBAL", null, "score");

        assertResult(engine, "score()", 2);
        assertTrue(registrar.hasRemoteFunction("PROJECT", "project-a", "score"));
        assertFalse(registrar.hasRemoteFunction("GLOBAL", null, "score"));
    }

    @Test
    public void successfulRemoteSnapshotReplacesOnlyRemoteFunctions() {
        QLExpressEngine engine = new QLExpressEngine();
        ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(engine, null, "project-a");
        registrar.registerOne(function("manual", "9"));
        registrar.registerRemoteFromPush("GLOBAL", null, "stale", "SCRIPT", "1",
                null, null, null, null);

        registrar.replaceRemoteSnapshot(Collections.singletonList(function("fresh", "2")));

        assertFalse(registrar.hasRemoteFunction("GLOBAL", null, "stale"));
        assertTrue(registrar.hasRemoteFunction("GLOBAL", null, "fresh"));
        assertResult(engine, "manual()", 9);
        assertResult(engine, "fresh()", 2);
    }

    @Test
    public void legacyPushWithoutScopeTreatsProjectCodeAsProjectAndAbsentCodeAsGlobal() {
        QLExpressEngine engine = new QLExpressEngine();
        ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(engine, null, "project-a");

        registrar.registerRemoteFromPush(null, "project-a", "score", "SCRIPT", "2",
                null, null, null, null);
        registrar.registerRemoteFromPush(null, null, "score", "SCRIPT", "1",
                null, null, null, null);

        assertResult(engine, "score()", 2);
        assertTrue(registrar.hasRemoteFunction("PROJECT", "project-a", "score"));
        assertTrue(registrar.hasRemoteFunction("GLOBAL", null, "score"));
    }

    @Test
    public void legacyRemoteMutationMethodsKeepVoidBinarySignature() throws Exception {
        Method register = ClientFunctionRegistrar.class.getMethod("registerRemoteFromPush",
                String.class, String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class);
        Method remove = ClientFunctionRegistrar.class.getMethod("removeRemote",
                String.class, String.class, String.class);

        assertEquals(Void.TYPE, register.getReturnType());
        assertEquals(Void.TYPE, remove.getReturnType());
    }

    private JSONObject function(String code, String script) {
        JSONObject function = new JSONObject();
        function.put("funcCode", code);
        function.put("implType", "SCRIPT");
        function.put("implScript", script);
        function.put("scope", "GLOBAL");
        return function;
    }

    private void assertResult(QLExpressEngine engine, String script, int expected) {
        RuleResult result = engine.execute(script, Collections.emptyMap(), false);
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(expected, ((Number) result.getResult()).intValue());
    }
}
