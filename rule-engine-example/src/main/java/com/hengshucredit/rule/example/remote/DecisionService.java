package com.hengshucredit.rule.example.remote;

import com.hengshucredit.rule.client.http.RuleHttpClient;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class DecisionService {
    private final RuleHttpClient client;
    private final List<String> rules;

    public DecisionService(RuleHttpClient client, Environment env) {
        this.client = client;
        // 不自动转换大小写；逗号用于配置多个允许执行的编码。
        rules = Arrays.stream(env.getRequiredProperty("RULE_ALLOWED_CODES").split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        if (rules.isEmpty()) throw new IllegalArgumentException("RULE_ALLOWED_CODES 不能为空");
    }

    public List<String> allowedRules() { return rules; }

    public RuleResult execute(String ruleCode, Map<String, Object> params) {
        if (!rules.contains(ruleCode)) throw new IllegalArgumentException("规则不在业务服务允许调用的列表中");
        return client.execute(ruleCode, params);
    }
}
