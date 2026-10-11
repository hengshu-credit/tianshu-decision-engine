package com.hengshucredit.rule.core.engine;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.compiler.OperandCompiler;
import com.hengshucredit.rule.core.compiler.ActionDataCompiler;
import com.hengshucredit.rule.core.compiler.VarContext;
import com.hengshucredit.rule.core.function.ManagedFunctionInvoker;
import org.junit.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ManagedFunctionInvokerTest {
    @Test public void nativeActionBlockUsesItsIdEvenWhenTheCachedCodeIsStale() {
        VarContext vars = new VarContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(41L, "numAdd"), Map.of(41L, 2), Set.of(41L));
        var actions = JSON.parseArray("[{\"type\":\"func-call\",\"functionId\":41,\"functionCode\":\"wrongDisplay\",\"args\":[{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":12},{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":3}]}]");
        assertEquals("invokeFunctionById(\"41\", 12, 3)", ActionDataCompiler.compile(actions, vars));
    }
    @Test public void compilerCarriesNativeIdInsteadOfBorrowingItsDisplayCode() {
        VarContext vars = new VarContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(41L, "numAdd"), Map.of(41L, 2), Set.of(41L));
        var operand = JSON.parseObject("{\"kind\":\"FUNCTION\",\"functionId\":41,\"functionCode\":\"wrongDisplay\",\"args\":[{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":12},{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":3}]}");
        String script = OperandCompiler.compile(operand, vars);
        assertEquals("invokeFunctionById(\"41\", 12, 3)", script);
        RequestContext context = new RequestContext();
        try (var bound = context.bindFunctions(Map.of(ManagedFunctionInvoker.bindingKey(41L), (q, args) -> {
            assertEquals(2, args.size()); assertEquals(12, args.getValue(0)); assertEquals(3, args.getValue(1));
            return 900;
        }))) {
            QLExpressEngine engine = new QLExpressEngine();
            var result = engine.execute(engine.prepare(script), Map.of(), false, context);
            assertTrue(result.getErrorMessage(), result.isSuccess()); assertEquals(900, result.getResult());
        }
    }

    @Test public void absentOrMalformedIdNeverFallsBackToAnyNameBinding() {
        QLExpressEngine engine = new QLExpressEngine(); RequestContext context = new RequestContext();
        AtomicInteger calls = new AtomicInteger();
        try (var bound = context.bindFunctions(Map.of("numAdd", (q, args) -> { calls.incrementAndGet(); return 7; }))) {
            for (String source : new String[]{"invokeFunctionById(\"41\",1,2)", "invokeFunctionById(\"1.5\",1)", "invokeFunctionById(\"9223372036854775808\",1)", "invokeFunctionById()"}) {
                assertFalse(source, engine.execute(engine.prepare(source), Map.of(), false, context).isSuccess());
            }
            assertEquals(0, calls.get());
        }
    }

    @Test public void idBindingsRestoreAcrossNestedScopesAndDoNotChangePlatformBuiltins() {
        QLExpressEngine engine = new QLExpressEngine(); RequestContext context = new RequestContext();
        var source = engine.prepare("invokeFunctionById(\"41\")");
        try (var outer = context.bindFunctions(Map.of("FUNCTION:41", (q, args) -> 1))) {
            assertEquals(1, engine.execute(source, Map.of(), false, context).getResult());
            try (var inner = context.bindFunctions(Map.of("FUNCTION:41", (q, args) -> 2))) {
                assertEquals(2, engine.execute(source, Map.of(), false, context).getResult());
                assertEquals(9, ((Number) engine.execute(engine.prepare("max(1,9,3)"), Map.of(), false, context).getResult()).intValue());
            }
            assertEquals(1, engine.execute(source, Map.of(), false, context).getResult());
        }
        assertFalse(engine.execute(source, Map.of(), false, context).isSuccess());
    }
}
