package com.hengshucredit.rule.core.function;

import com.alibaba.qlexpress4.Express4Runner;
import com.hengshucredit.rule.core.engine.ResourceVersionCall;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import java.util.ArrayList;
import java.util.List;

/** 执行器必须显式绑定版本读取器；不能退回当前环境同名或同号资源。 */
public final class ResourceVersionFunctions {
    public static final String VALUE = "resourceVersionValue";
    public static final String FUNCTION = "invokeFunctionVersionById";
    public static final String STATUS = "resourceVersionStatus";
    private ResourceVersionFunctions() { }

    public static void register(Express4Runner runner) {
        runner.addFunction(STATUS, (context, parameters) -> {
            if (parameters.size() != 5) throw new IllegalArgumentException("固定版本状态参数不完整");
            String token = String.valueOf(parameters.getValue(2));
            return RuntimeContextBridge.currentContext().resourceVersionValue(new ResourceVersionCall(
                    String.valueOf(parameters.getValue(0)), id(parameters.getValue(1)), versionId(token), "", List.of(),
                    String.valueOf(parameters.getValue(3)), String.valueOf(parameters.getValue(4)), source(token)));
        });
        runner.addFunction(VALUE, (context, parameters) -> {
            if (parameters.size() != 4) throw new IllegalArgumentException("固定版本字段参数不完整");
            String token = String.valueOf(parameters.getValue(2));
            return RuntimeContextBridge.currentContext().resourceVersionValue(new ResourceVersionCall(
                    String.valueOf(parameters.getValue(0)), id(parameters.getValue(1)), versionId(token),
                    String.valueOf(parameters.getValue(3)), List.of(), null, null, source(token)));
        });
        runner.addFunction(FUNCTION, (context, parameters) -> {
            if (parameters.size() < 2) throw new IllegalArgumentException("固定版本方法参数不完整");
            List<Object> args = new ArrayList<>();
            for (int i = 2; i < parameters.size(); i++) args.add(parameters.getValue(i));
            String version = String.valueOf(parameters.getValue(1));
            String source = version.startsWith("FUNCTION_HISTORY:") ? "FUNCTION_HISTORY" : version.startsWith("BUSINESS_VERSION:") ? "BUSINESS_VERSION" : null;
            return RuntimeContextBridge.currentContext().resourceVersionValue(new ResourceVersionCall(
                    "FUNCTION", id(parameters.getValue(0)), id(source == null ? version : version.substring(source.length() + 1)), "", args, null, null, source));
        });
    }

    private static Long id(Object value) {
        if (!(value instanceof String text) || !text.matches("[1-9][0-9]*"))
            throw new IllegalArgumentException("固定版本调用缺少明确的 ID");
        try { return Long.valueOf(text); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("固定版本调用 ID 超出范围", error); }
    }

    private static String source(String token) { return token.contains(":") ? token.substring(0, token.indexOf(':')) : null; }
    private static Long versionId(String token) { return id(token.contains(":") ? token.substring(token.indexOf(':') + 1) : token); }
}
