package com.hengshucredit.rule.core.function;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.Value;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;

/** 受管原生函数使用当前制品的 ID 绑定；不借用同名内置或当前环境函数。 */
public final class ManagedFunctionInvoker {
    public static final String NAME = "invokeFunctionById";
    private ManagedFunctionInvoker() { }

    public static String bindingKey(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("函数引用 ID 必须为正整数");
        return "FUNCTION:" + id;
    }

    public static void register(Express4Runner runner) {
        runner.addFunction(NAME, (context, parameters) -> {
            Object value = parameters.size() == 0 ? null : parameters.getValue(0);
            if (!(value instanceof String id) || !id.matches("[1-9][0-9]*"))
                throw new IllegalArgumentException("受管函数调用缺少明确的 ID");
            String key;
            try { key = bindingKey(Long.valueOf(id)); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("函数引用 ID 超出范围", error); }
            var target = RuntimeContextBridge.currentContext().function(key);
            if (target == null) throw new IllegalStateException("当前规则制品未绑定函数 ID=" + id);
            Parameters arguments = new Parameters() {
                @Override public Value get(int index) { return index < 0 || index >= size() ? null : parameters.get(index + 1); }
                @Override public int size() { return parameters.size() - 1; }
            };
            return target.call(context, arguments);
        });
    }
}
