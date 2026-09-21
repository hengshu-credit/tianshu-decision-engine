package com.hengshucredit.rule.server.functions;

import com.hengshucredit.rule.server.service.ExternalApiInvokeService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** 供决策流显式调用已配置外数接口，沿用统一鉴权、超时和调用日志。 */
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
        return externalApiInvokeService.invoke(id, request);
    }
}
