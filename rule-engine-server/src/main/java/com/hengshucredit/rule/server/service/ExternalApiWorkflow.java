package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/** 顺序 N 步协议；已完成步骤不会因后续失败被重新执行。所有等待共享调用截止时间。 */
public final class ExternalApiWorkflow {
    @FunctionalInterface public interface Transport {
        Map<String, Object> request(JSONObject step, Map<String, Object> context);
    }
    private ExternalApiWorkflow() { }

    public static Map<String, Object> execute(JSONObject specification, Map<String, Object> input,
            Transport transport, ExternalApiCallbackStore callbacks,
            BiPredicate<Object, Map<String, Object>> condition) throws java.util.concurrent.TimeoutException {
        return execute(specification, input, transport, callbacks, condition, (step, response) -> { });
    }

    public static Map<String, Object> execute(JSONObject specification, Map<String, Object> input,
            Transport transport, ExternalApiCallbackStore callbacks,
            BiPredicate<Object, Map<String, Object>> condition,
            java.util.function.BiConsumer<JSONObject, Map<String, Object>> observer) throws java.util.concurrent.TimeoutException {
        return execute(specification, input, transport, callbacks, condition, observer, null);
    }

    /** 从已完成步骤检查点恢复；恢复时跳过提交过的步骤，只继续当前等待步骤。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> execute(JSONObject specification, Map<String, Object> input,
            Transport transport, ExternalApiCallbackStore callbacks,
            BiPredicate<Object, Map<String, Object>> condition,
            java.util.function.BiConsumer<JSONObject, Map<String, Object>> observer,
            Map<String, Object> resume) throws java.util.concurrent.TimeoutException {
        Map<String, Object> context = new LinkedHashMap<>();
        context.putAll(input);
        context.put("input", input);
        Map<String, Object> results = new LinkedHashMap<>();
        Map<String, Object> callbackUrls = new LinkedHashMap<>();
        Map<String, String> registrations = new LinkedHashMap<>();
        boolean pending = false;
        String currentStepId = null;
        if (resume != null) {
            Object storedResults = resume.get("results");
            if (storedResults instanceof Map<?, ?> map) map.forEach((key, value) -> {
                if (value instanceof Map<?, ?> item) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    item.forEach((itemKey, itemValue) -> copy.put(String.valueOf(itemKey), itemValue));
                    results.put(String.valueOf(key), copy);
                }
            });
            Object storedRegistrations = resume.get("registrations");
            if (storedRegistrations instanceof Map<?, ?> map) map.forEach((key, value) -> registrations.put(String.valueOf(key), String.valueOf(value)));
            Object storedCallbacks = resume.get("callbacks");
            if (storedCallbacks instanceof Map<?, ?> map) map.forEach((key, value) -> callbackUrls.put(String.valueOf(key), value));
        }
        context.put("steps", results);
        context.put("callbacks", callbackUrls);
        Map<String, Object> last = new LinkedHashMap<>();
        try {
            // 先注册回调，前面的提交请求即可引用 callbacks.<稳定步骤ID>.url。
            if (resume == null) for (Object raw : specification.getJSONArray("steps")) {
                JSONObject step = JSON.parseObject(JSON.toJSONString(raw));
                if (!"CALLBACK".equals(step.getString("type"))) continue;
                String id = step.getString("id");
                JSONObject callback = step.getJSONObject("callback");
                String invocationId = callbacks.register(callback);
                registrations.put(id, invocationId);
                callbackUrls.put(id, Map.of("url", callback.getString("url").replace("${invocationId}", invocationId)));
            }
            for (Object raw : specification.getJSONArray("steps")) {
                JSONObject step = JSON.parseObject(JSON.toJSONString(raw));
                String id = step.getString("id");
                currentStepId = id;
                if (resume != null && !id.equals(resume.get("stepId")) && results.containsKey(id)) {
                    Object stored = results.get(id);
                    if (stored instanceof Map<?, ?> map) {
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            last.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                    }
                    continue;
                }
                RequestDeadlineContext.check();
                if (step.get("when") != null && !condition.test(step.get("when"), context)) {
                    results.put(id, Map.of("status", "SKIPPED"));
                    continue;
                }
                Map<String, Object> response;
            int attempts = 0;
                if ("CALLBACK".equals(step.getString("type"))) {
                    String invocationId = registrations.get(id);
                    do {
                        RequestDeadlineContext.check();
                        response = callbacks.result(invocationId);
                        if (response == null) pause(100);
                    } while (response == null);
                    JSONObject callback = step.getJSONObject("callback");
                    Object status = ExternalApiRequestPlan.read(response, callback.getString("statusPath"));
                    boolean accepted = hasCondition(callback.get("successCondition"))
                            ? condition.test(callback.get("successCondition"), response) : String.valueOf(status).equals(callback.getString("successValue"));
                    if (!accepted) {
                        response.put("success", false);
                        observer.accept(step, response);
                        throw new IllegalStateException("回调步骤返回失败状态: " + id);
                    }
                } else {
                    JSONObject poll = step.getJSONObject("poll");
                    int limit = poll == null ? 1 : ExternalApiConfigValidator.nonNegative(poll, "maxAttempts", 20);
                    boolean continueOnFailure = poll != null && "CONTINUE".equalsIgnoreCase(poll.getString("failureMode"));
                    while (true) {
                        RequestDeadlineContext.check();
                        response = transport.request(step, context);
                        attempts++;
                        Map<String, Object> observed = new LinkedHashMap<>(context);
                        observed.putAll(response);
                        if (poll == null || poll.get("until") == null || condition.test(poll.get("until"), observed)) break;
                    if (hasCondition(poll.get("failure")) && condition.test(poll.get("failure"), observed)
                            && !continueOnFailure) {
                            throw new IllegalStateException("外数步骤失败条件命中: " + id);
                        }
                    if (limit > 0 && attempts >= limit) throw new IllegalStateException("外数步骤轮询次数已用尽: " + id);
                        long interval = ExternalApiConfigValidator.positive(poll, "intervalMs", 1000);
                        double backoff = poll.get("backoffMultiplier") == null ? 1 : poll.getDoubleValue("backoffMultiplier");
                        long maximum = poll.get("maxIntervalMs") == null ? 60000 : poll.getLongValue("maxIntervalMs");
                        pause(Math.min(maximum, (long) (interval * Math.pow(backoff, attempts - 1))));
                    }
                }
                Map<String, Object> stored = new LinkedHashMap<>(response);
                stored.put("stepId", id);
                stored.put("pollAttempts", attempts);
                results.put(id, stored);
                observer.accept(step, stored);
                last = response;
            }
            Map<String, Object> result = new LinkedHashMap<>(last);
            result.put("steps", results);
            result.put("success", !Boolean.FALSE.equals(last.get("success")));
            return result;
        } catch (java.util.concurrent.TimeoutException timeout) {
            if (currentStepId != null) {
                pending = true;
                throw new PendingException(currentStepId, results, registrations, callbackUrls, timeout);
            }
            throw timeout;
        } finally {
            if (!pending) for (String registration : registrations.values()) callbacks.close(registration);
        }
    }

    public static final class PendingException extends RuntimeException {
        private final String stepId;
        private final Map<String, Object> results;
        private final Map<String, String> registrations;
        private final Map<String, Object> callbacks;

        private PendingException(String stepId, Map<String, Object> results,
                                 Map<String, String> registrations, Map<String, Object> callbacks,
                                 Throwable cause) {
            super("外数链路等待超时，待恢复步骤：" + stepId, cause);
            this.stepId = stepId;
            this.results = new LinkedHashMap<>(results);
            this.registrations = new LinkedHashMap<>(registrations);
            this.callbacks = new LinkedHashMap<>(callbacks);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", "WORKFLOW");
            result.put("stepId", stepId);
            result.put("results", results);
            result.put("registrations", registrations);
            result.put("callbacks", callbacks);
            return result;
        }
    }

    static boolean hasCondition(Object condition) {
        if (!(condition instanceof Map<?, ?> map) || map.isEmpty()) return false;
        if (map.get("children") instanceof Iterable<?> children) {
            for (Object child : children) if (hasCondition(child)) return true;
            return false;
        }
        return map.get("path") != null && !String.valueOf(map.get("path")).isBlank() || map.get("left") != null;
    }

    private static void pause(long milliseconds) throws java.util.concurrent.TimeoutException {
        RequestDeadlineContext.check();
        try {
            Thread.sleep(Math.max(1, Math.min(milliseconds, RequestDeadlineContext.remainingMillis())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("外数链路等待已中断", e);
        }
    }
}
