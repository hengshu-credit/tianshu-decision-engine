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
