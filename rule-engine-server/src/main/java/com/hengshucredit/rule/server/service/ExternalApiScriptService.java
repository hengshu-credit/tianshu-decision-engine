package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.hengshucredit.rule.core.engine.QLExpressScriptSecurity;
import com.hengshucredit.rule.core.function.AggregateBuiltinFunctionRegistry;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 执行外数 API 请求/响应脚本，不加载业务 Bean 或用户自定义 Java 函数。 */
@Service
public class ExternalApiScriptService {

    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private final Express4Runner runner;

    public ExternalApiScriptService() {
        runner = new Express4Runner(InitOptions.builder()
                .traceExpression(false)
                .securityStrategy(QLExpressScriptSecurity.standardFunctionWhitelist())
                .build());
        AggregateBuiltinFunctionRegistry.register(runner);
        registerApiFunctions(new ExternalApiScriptFunctions());
    }

    public Object executeRequest(String script, Map<String, Object> context) {
        return execute("请求", script, context);
    }

    public Object executeResponse(String script, Map<String, Object> context) {
        return execute("响应", script, context);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseScriptVariables(String authApiConfig) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (authApiConfig == null || authApiConfig.trim().isEmpty()) return result;
        Map<String, Object> config;
        try {
            config = JSON.parseObject(authApiConfig, LinkedHashMap.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("接口鉴权配置不是有效 JSON");
        }
        Object rows = config.get("scriptVariables");
        if (!(rows instanceof List)) return result;
        for (Object rowObject : (List<?>) rows) {
            if (!(rowObject instanceof Map)) continue;
            Map<String, Object> row = (Map<String, Object>) rowObject;
            String name = row.get("name") == null ? "" : String.valueOf(row.get("name")).trim();
            if (name.isEmpty()) continue;
            if (!VARIABLE_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("脚本变量名格式错误: " + name);
            }
            if (result.containsKey(name)) {
                throw new IllegalArgumentException("脚本变量名重复: " + name);
            }
            result.put(name, row.get("value"));
        }
        return result;
    }

    private Object execute(String phase, String script, Map<String, Object> context) {
        if (script == null || script.trim().isEmpty()) {
            return context == null ? null : context.get("body");
        }
        Map<String, Object> safeContext = context == null ? new LinkedHashMap<>() : context;
        try {
            QLResult result = runner.execute(script, safeContext, QLOptions.builder().cache(true).build());
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
        runner.addFunctionOfServiceMethod("apiHmacSha256Base64", functions, "apiHmacSha256Base64", new Class<?>[]{String.class, String.class});
        runner.addFunctionOfServiceMethod("apiTripleDesEncryptBase64", functions, "apiTripleDesEncryptBase64", new Class<?>[]{String.class, String.class});
        runner.addFunctionOfServiceMethod("apiTripleDesDecryptBase64", functions, "apiTripleDesDecryptBase64", new Class<?>[]{String.class, String.class});
        runner.addFunctionOfServiceMethod("apiRsaEncryptBase64", functions, "apiRsaEncryptBase64", new Class<?>[]{String.class, String.class});
        runner.addFunctionOfServiceMethod("apiRsaSignBase64", functions, "apiRsaSignBase64", new Class<?>[]{String.class, String.class, String.class});
    }
}
