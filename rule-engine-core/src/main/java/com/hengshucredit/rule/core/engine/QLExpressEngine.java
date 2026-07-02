package com.hengshucredit.rule.core.engine;

import com.hengshucredit.rule.core.function.AggregateBuiltinFunctionRegistry;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.alibaba.fastjson.JSON;
import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.security.QLSecurityStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QLExpressEngine {

    private static final Logger log = LoggerFactory.getLogger(QLExpressEngine.class);
    private static final int MAX_TRACE_JSON_LENGTH = 100_000;

    private final Express4Runner runner;

    public QLExpressEngine() {
        this.runner = new Express4Runner(InitOptions.builder()
                .traceExpression(true)
                .securityStrategy(QLSecurityStrategy.isolation())
                .build());
        AggregateBuiltinFunctionRegistry.register(this.runner);
    }

    public QLExpressEngine(InitOptions initOptions) {
        this.runner = new Express4Runner(initOptions);
        AggregateBuiltinFunctionRegistry.register(this.runner);
    }

    public RuleResult execute(String script, Map<String, Object> context) {
        return execute(script, context, false);
    }

    public RuleResult execute(String script, Map<String, Object> context, boolean trace) {
        RuleResult ruleResult = new RuleResult();
        long start = System.currentTimeMillis();
        try {
            QLOptions options = QLOptions.builder()
                    .cache(true)
                    .traceExpression(trace)
                    .build();
            QLResult result = runner.execute(script, context != null ? context : Collections.emptyMap(), options);
            ruleResult.setResult(result.getResult());
            ruleResult.setSuccess(true);
            if (trace && result.getExpressionTraces() != null) {
                ruleResult.setTraces(buildBoundedTraces(result.getExpressionTraces()));
            }
        } catch (StackOverflowError e) {
            log.error("QLExpress execution stack overflow", e);
            ruleResult.setSuccess(false);
            ruleResult.setErrorMessage("QLExpress execution failed: StackOverflowError");
        } catch (Exception e) {
            log.error("QLExpress execution error: {}", e.getMessage(), e);
            ruleResult.setSuccess(false);
            ruleResult.setErrorMessage(e.getMessage());
        } finally {
            ruleResult.setExecuteTimeMs(System.currentTimeMillis() - start);
        }
        return ruleResult;
    }

    public RuleResult execute(String script, Object context, boolean trace) {
        RuleResult ruleResult = new RuleResult();
        long start = System.currentTimeMillis();
        try {
            QLOptions options = QLOptions.builder()
                    .cache(true)
                    .traceExpression(trace)
                    .build();
            QLResult result = runner.execute(script, context != null ? context : Collections.emptyMap(), options);
            ruleResult.setResult(result.getResult());
            ruleResult.setSuccess(true);
            if (trace && result.getExpressionTraces() != null) {
                ruleResult.setTraces(buildBoundedTraces(result.getExpressionTraces()));
            }
        } catch (StackOverflowError e) {
            log.error("QLExpress execution stack overflow", e);
            ruleResult.setSuccess(false);
            ruleResult.setErrorMessage("QLExpress execution failed: StackOverflowError");
        } catch (Exception e) {
            log.error("QLExpress execution error: {}", e.getMessage(), e);
            ruleResult.setSuccess(false);
            ruleResult.setErrorMessage(e.getMessage());
        } finally {
            ruleResult.setExecuteTimeMs(System.currentTimeMillis() - start);
        }
        return ruleResult;
    }

    public Express4Runner getRunner() {
        return runner;
    }

    private List<Object> buildBoundedTraces(Object expressionTraces) {
        String traceJson;
        try {
            traceJson = JSON.toJSONString(expressionTraces);
        } catch (StackOverflowError e) {
            log.warn("QLExpress trace serialization stack overflow", e);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("type", "TRACE_SERIALIZE_FAILED");
            summary.put("message", "Execution trace is too complex to serialize. Use input and output data for diagnosis.");
            summary.put("maxJsonLength", MAX_TRACE_JSON_LENGTH);
            return Collections.<Object>singletonList(summary);
        } catch (Exception e) {
            log.warn("QLExpress trace serialization failed: {}", e.getMessage());
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("type", "TRACE_SERIALIZE_FAILED");
            summary.put("message", "Execution trace serialization failed: " + e.getMessage());
            summary.put("maxJsonLength", MAX_TRACE_JSON_LENGTH);
            return Collections.<Object>singletonList(summary);
        }
        if (traceJson.length() <= MAX_TRACE_JSON_LENGTH) {
            return Collections.singletonList(expressionTraces);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", "TRACE_TRUNCATED");
        summary.put("message", "执行轨迹过大，已截断。请结合执行结果、入参和模型矩阵分析命中路径。");
        summary.put("originalJsonLength", traceJson.length());
        summary.put("maxJsonLength", MAX_TRACE_JSON_LENGTH);
        return Collections.<Object>singletonList(summary);
    }
}
