package com.hengshucredit.rule.server.functions;

import com.hengshucredit.rule.server.service.ExternalApiInvokeService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** 旧 Bean 名保留迁移诊断；规则只允许通过外数变量或对象触发调用。 */
@Service("externalApiFunctions")
public class ExternalApiFunctions {
    private final ExternalApiInvokeService externalApiInvokeService;

    public ExternalApiFunctions(ExternalApiInvokeService externalApiInvokeService) {
        this.externalApiInvokeService = externalApiInvokeService;
    }

    public Map<String, Object> invoke(Object apiConfigId, Object params) {
        final long id;
        try {
            id = new BigDecimal(String.valueOf(apiConfigId)).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("外数接口 ID 必须是正整数", e);
        }
        if (id <= 0) throw new IllegalArgumentException("外数接口 ID 必须是正整数");
        if (!(params instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("外数请求参数必须是映射对象");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("外数请求参数名称必须是字符串");
            }
            request.put(key, entry.getValue());
        }
        throw new IllegalStateException("外数显式函数调用已停用，请引用 API 外数变量或数据对象，以统一请求校验和调用次数");
    }
}
