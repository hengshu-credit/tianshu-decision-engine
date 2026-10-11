package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RequestContext;
import com.hengshucredit.rule.core.engine.ResourceVersionCall;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import org.junit.Assert;
import org.junit.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ResourceVersionFunctionsTest {
    @Test public void businessVersionTokenReachesValueAndStatusResolvers() {
        var operand = JSON.parseObject("{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":7,\"versionMode\":\"FIXED\",\"versionId\":12,\"versionSource\":\"BUSINESS_VERSION\"}");
        RequestContext context = new RequestContext();
        try (var installed = RuntimeContextBridge.install(context); var bound = context.bindResourceVersions(call -> {
            Assert.assertEquals("VARIABLE:7@BUSINESS_VERSION:12", call.bindingKey());
            return call.statusDimension() == null ? 23 : true;
        })) {
            Assert.assertEquals(23, new QLExpressEngine().execute(OperandCompiler.compile(operand, null), Map.of(), false).getResult());
            Assert.assertEquals(true, new QLExpressEngine().execute(SourceStatusExpressionBuilder.build(operand, "source_success"), Map.of(), false).getResult());
        }
    }
    @Test public void fixedReferenceUsesVersionIdentityInsteadOfCurrentConstantOrDisplayCode() {
        var operand = JSON.parseObject("{\"kind\":\"REFERENCE\",\"refType\":\"CONSTANT\",\"refId\":\"9007199254740993\",\"code\":\"old_name\",\"versionMode\":\"FIXED\",\"versionId\":\"88\"}");
        String script = OperandCompiler.compile(operand, new VarContext(Map.of()));
        AtomicReference<ResourceVersionCall> captured = new AtomicReference<>();
        RequestContext context = new RequestContext();
        try (var installed = RuntimeContextBridge.install(context);
             var bound = context.bindResourceVersions(call -> { captured.set(call); return 17; })) {
            var result = new QLExpressEngine().execute(script, Map.of("old_name", 99), false);
            Assert.assertTrue(result.getErrorMessage(), result.isSuccess());
            Assert.assertEquals(17, ((Number) result.getResult()).intValue());
            Assert.assertEquals(Long.valueOf(9007199254740993L), captured.get().refId());
            Assert.assertEquals(Long.valueOf(88), captured.get().versionId());
        }
    }

    @Test public void fixedFunctionReceivesEvaluatedArgumentsAndCannotFallBackToLatest() {
        String script = OperandCompiler.compile(JSON.parseObject("{\"kind\":\"FUNCTION\",\"functionId\":7,\"functionCode\":\"max\",\"versionMode\":\"FIXED\",\"versionId\":12,\"args\":[{\"kind\":\"LITERAL\",\"value\":3,\"valueType\":\"NUMBER\"}]}"), null);
        RequestContext context = new RequestContext();
        try (var installed = RuntimeContextBridge.install(context)) {
            var unbound = new QLExpressEngine().execute(script, Map.of(), false);
            Assert.assertFalse(unbound.isSuccess());
            try (var bound = context.bindResourceVersions(call -> {
                Assert.assertEquals("FUNCTION:7@12", call.bindingKey());
                Assert.assertEquals(3, ((Number) call.arguments().get(0)).intValue());
                return 6;
            })) {
                var result = new QLExpressEngine().execute(script, Map.of(), false);
                Assert.assertTrue(result.getErrorMessage(), result.isSuccess());
                Assert.assertEquals(6, ((Number) result.getResult()).intValue());
            }
            Assert.assertThrows(IllegalStateException.class, () -> context.resourceVersionValue(new ResourceVersionCall("FUNCTION", 7L, 12L, "", List.of())));
        }
    }

    @Test public void invalidFixedVersionFailsDuringCompile() {
        Assert.assertThrows(IllegalArgumentException.class, () -> OperandCompiler.compile(JSON.parseObject("{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":7,\"versionMode\":\"FIXED\"}"), null));
        Assert.assertThrows(IllegalArgumentException.class, () -> OperandCompiler.compile(JSON.parseObject("{\"kind\":\"LITERAL\",\"versionMode\":\"FIXED\",\"versionId\":8,\"value\":1}"), null));
        Assert.assertThrows(IllegalArgumentException.class, () -> OperandCompiler.compile(JSON.parseObject("{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":7,\"versionMode\":\"FIXED\",\"versionId\":1.5}"), null));
    }

    @Test public void originalFunctionHistoryKeepsItsNamespaceThroughCompilation() {
        String script = OperandCompiler.compile(JSON.parseObject("{\"kind\":\"FUNCTION\",\"functionId\":7,\"versionMode\":\"FIXED\",\"versionId\":12,\"versionSource\":\"FUNCTION_HISTORY\",\"args\":[]}"), null);
        RequestContext context = new RequestContext();
        try (var installed = RuntimeContextBridge.install(context); var bound = context.bindResourceVersions(call -> {
            Assert.assertEquals("FUNCTION:7@FUNCTION_HISTORY:12", call.bindingKey()); return 3;
        })) {
            var result = new QLExpressEngine().execute(script, Map.of(), false);
            Assert.assertTrue(result.getErrorMessage(), result.isSuccess()); Assert.assertEquals(3, result.getResult());
        }
    }

    @Test public void fixedSourceStatusPassesVersionAndStatusDimensionToResolver() {
        String script = SourceStatusExpressionBuilder.build(JSON.parseObject("{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":7,\"versionMode\":\"FIXED\",\"versionId\":12}"), "source_success");
        RequestContext context = new RequestContext();
        try (var installed = RuntimeContextBridge.install(context); var bound = context.bindResourceVersions(call -> {
            Assert.assertEquals("VARIABLE:7@12", call.bindingKey()); Assert.assertEquals("OUTCOME", call.statusDimension());
            Assert.assertEquals("SUCCESS", call.expectedStatus()); return true;
        })) { Assert.assertEquals(true, new QLExpressEngine().execute(script, Map.of(), false).getResult()); }
    }

    @Test public void latestKeepsExistingReferenceContract() {
        String script = OperandCompiler.compile(JSON.parseObject("{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":7,\"code\":\"amount\",\"versionMode\":\"LATEST\",\"versionId\":8}"), null);
        Assert.assertEquals("amount", script);
    }
}
