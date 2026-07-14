package com.hengshucredit.rule.core.function;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 在 Java 运行时上下文中复用与规则脚本完全相同的内置函数注册表。 */
public final class BuiltinFunctionInvoker {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final QLExpressEngine ENGINE = new QLExpressEngine();

    private BuiltinFunctionInvoker() {
    }

    public static Object invoke(String functionCode, List<Object> args) {
        if (functionCode == null || !IDENTIFIER.matcher(functionCode).matches()) {
            throw new IllegalArgumentException("内置函数编码不合法: " + functionCode);
        }
        Map<String, Object> context = new LinkedHashMap<>();
        StringBuilder script = new StringBuilder(functionCode).append('(');
        if (args != null) {
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) script.append(", ");
                String name = "__operand_arg_" + i;
                context.put(name, args.get(i));
                script.append(name);
            }
        }
        RuleResult result = ENGINE.execute(script.append(')').toString(), context);
        if (!result.isSuccess()) {
            throw new IllegalArgumentException("不支持的内置函数或参数不正确: " + functionCode
                    + (result.getErrorMessage() == null ? "" : "，" + result.getErrorMessage()));
        }
        return result.getResult();
    }
}
