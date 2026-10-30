package com.hengshucredit.rule.client.function;

import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ClientNativeFunctionIsolationTest {
    @Test public void frozenAndRemoteDefinitionsCannotExposeHostEnvironment() {
        var engine = new QLExpressEngine(); var registrar = new ClientFunctionRegistrar(engine, null, "test");
        var function = new JSONObject(); function.put("id", 1); function.put("funcCode", "readHost");
        function.put("implType", "JAVA"); function.put("implClass", "java.lang.System");
        function.put("implMethod", "getenv"); function.put("paramsJson", "[]"); function.put("scope", "GLOBAL");
        assertThrows(IllegalStateException.class, () -> registrar.prepareFrozen(List.of(function)));
        assertThrows(IllegalStateException.class, () -> registrar.replaceRemoteSnapshot(List.of(function)));
        assertFalse(engine.execute("readHost()", java.util.Map.of()).isSuccess());
    }
}
