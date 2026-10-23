package com.hengshucredit.rule.core.function;

import com.hengshucredit.rule.core.engine.QLExpressEngineFactory;

import java.util.List;
import java.util.regex.Pattern;

/** 共享 SCRIPT 函数声明校验，保证保存、编译和运行使用同一参数协议。 */
public final class FunctionScriptContract {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private FunctionScriptContract() { }

    public static String requireIdentifier(String value) {
        if (value == null || !IDENTIFIER.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("函数编码不是合法标识符: " + value);
        }
        return value.trim();
    }

    /** 校验脚本、函数编码和参数定义，但不执行用户脚本。 */
    public static void validate(String functionCode, String script, String paramsJson) {
        QLExpressEngineFactory.getInstance().parseToSerializableCache(declaration(functionCode, script, paramsJson));
    }

    public static String declaration(String functionCode, String script, String paramsJson) {
        String code = requireIdentifier(functionCode);
        if (script == null || script.trim().isEmpty()) {
            throw new IllegalArgumentException("SCRIPT 函数实现脚本不能为空");
        }
        List<String> parameters = FunctionParameterContract.names(paramsJson);
        return new StringBuilder("function ")
                .append(code).append('(').append(String.join(", ", parameters)).append(") {\n")
                .append(script.trim()).append("\n}").toString();
    }
}
