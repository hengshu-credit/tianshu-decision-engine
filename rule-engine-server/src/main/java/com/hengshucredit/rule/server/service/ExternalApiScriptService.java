package com.hengshucredit.rule.server.service;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.hengshucredit.rule.core.engine.QLExpressScriptSecurity;
import com.hengshucredit.rule.core.function.AggregateBuiltinFunctionRegistry;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 执行外数 API 请求/响应脚本，不加载业务 Bean 或用户自定义 Java 函数。 */
@Service
public class ExternalApiScriptService {

    private final Express4Runner runner;

    public ExternalApiScriptService() {
        runner = new Express4Runner(InitOptions.builder()
                .traceExpression(false)
                .securityStrategy(QLExpressScriptSecurity.standardFunctionWhitelist())
                .build());
        AggregateBuiltinFunctionRegistry.register(runner, false);
        registerApiFunctions(new ExternalApiScriptFunctions());
    }

    public Object executeRequest(String script, Map<String, Object> context) {
        return execute("请求", script, context);
    }

    public Object executeResponse(String script, Map<String, Object> context) {
        return execute("响应", script, context);
    }

    private Object execute(String phase, String script, Map<String, Object> context) {
        if (script == null || script.trim().isEmpty()) {
            return context == null ? null : context.get("body");
        }
        Map<String, Object> safeContext = context == null ? new LinkedHashMap<>() : context;
        try {
            QLResult result = runner.execute(script, safeContext, QLOptions.builder()
                    .cache(true)
                    .timeoutMillis(2000L)
                    .maxArrLength(10000)
                    .build());
            return result.getResult() == null ? safeContext.get("body") : result.getResult();
        } catch (Exception e) {
            throw new IllegalArgumentException(phase + "脚本执行失败：" + sanitize(e.getMessage(), safeContext));
        }
    }

    private String sanitize(String message, Map<String, Object> context) {
        String result = message == null || message.trim().isEmpty() ? "脚本语法或函数调用错误" : message;
        Object vars = context.get("vars");
        if (vars instanceof Map) {
            for (Object value : ((Map<?, ?>) vars).values()) {
                if (value != null && !String.valueOf(value).isEmpty()) {
                    result = result.replace(String.valueOf(value), "******");
                }
            }
        }
        Object token = context.get("token");
        if (token != null && !String.valueOf(token).isEmpty()) {
            result = result.replace(String.valueOf(token), "******");
        }
        return result;
    }

    private void registerApiFunctions(ExternalApiScriptFunctions functions) {
        runner.addFunctionOfServiceMethod("apiUuid32", functions, "apiUuid32", new Class<?>[]{});
        runner.addFunctionOfServiceMethod("apiTimestampMillis", functions, "apiTimestampMillis", new Class<?>[]{});
        runner.addFunctionOfServiceMethod("apiRandomBase64", functions, "apiRandomBase64", new Class<?>[]{double.class});
        runner.addFunctionOfServiceMethod("apiTimestamp", functions, "apiTimestamp", new Class<?>[]{String.class});
        runner.addFunctionOfServiceMethod("apiUrlEncode", functions, "apiUrlEncode", new Class<?>[]{String.class});
        runner.addFunctionOfServiceMethod("apiBase64Encode", functions, "apiBase64Encode", new Class<?>[]{String.class});
        runner.addFunctionOfServiceMethod("apiBase64Decode", functions, "apiBase64Decode", new Class<?>[]{String.class});
        runner.addFunctionOfServiceMethod("apiMd5", functions, "apiMd5", new Class<?>[]{String.class});
        runner.addFunctionOfServiceMethod("apiSha1", functions, "apiSha1", new Class<?>[]{String.class});
        runner.addFunctionOfServiceMethod("apiSha256", functions, "apiSha256", new Class<?>[]{String.class});
        runner.addFunctionOfServiceMethod("apiSm3", functions, "apiSm3", new Class<?>[]{String.class});
        runner.addFunctionOfServiceMethod("apiPut", functions, "apiPut", new Class<?>[]{Object.class, String.class, Object.class});
        runner.addFunctionOfServiceMethod("apiRemove", functions, "apiRemove", new Class<?>[]{Object.class, String.class});
        runner.addFunctionOfServiceMethod("apiHmacSha1Base64", functions, "apiHmacSha1Base64", new Class<?>[]{String.class, String.class});
        runner.addFunctionOfServiceMethod("apiHmacSha1Base64Key", functions, "apiHmacSha1Base64Key", new Class<?>[]{String.class, String.class});
        runner.addFunctionOfServiceMethod("apiHmacSha256Base64", functions, "apiHmacSha256Base64", new Class<?>[]{String.class, String.class});
        runner.addFunctionOfServiceMethod("apiHmacSha256Base64Key", functions, "apiHmacSha256Base64Key", new Class<?>[]{String.class, String.class});
        runner.addFunctionOfServiceMethod("apiSortedKeyValue", functions, "apiSortedKeyValue", new Class<?>[]{Object.class, String.class, String.class});
        runner.addFunctionOfServiceMethod("apiTripleDesEncryptBase64", functions, "apiTripleDesEncryptBase64", new Class<?>[]{String.class, String.class});
        runner.addFunctionOfServiceMethod("apiTripleDesDecryptBase64", functions, "apiTripleDesDecryptBase64", new Class<?>[]{String.class, String.class});
        runner.addFunctionOfServiceMethod("apiRsaEncryptBase64", functions, "apiRsaEncryptBase64", new Class<?>[]{String.class, String.class});
        runner.addFunctionOfServiceMethod("apiRsaSignBase64", functions, "apiRsaSignBase64", new Class<?>[]{String.class, String.class, String.class});
    }
}
