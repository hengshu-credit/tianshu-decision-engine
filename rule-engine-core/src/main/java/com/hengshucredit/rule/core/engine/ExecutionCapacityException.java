package com.hengshucredit.rule.core.engine;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

/** 平台资源未受理执行，调用方可稍后重试；不属于模型业务计算失败。 */
public class ExecutionCapacityException extends RejectedExecutionException {
    public static final long RETRY_AFTER_MS = 1000L;
    private final Resource resource;

    public ExecutionCapacityException(Resource resource, String message) {
        super(message);
        this.resource = Objects.requireNonNull(resource, "capacity resource");
    }

    public Resource getResource() {
        return resource;
    }

    public static ExecutionCapacityException find(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ExecutionCapacityException capacity) return capacity;
        }
        return null;
    }

    public enum Resource {
        ONNX_SESSION_CACHE("onnx-session-cache"),
        PMML_MODEL_LOADING("pmml-model-loading"),
        MODEL_EXECUTION("model-execution"),
        DATABASE_QUERY("database-query");

        private final String limitName;

        Resource(String limitName) {
            this.limitName = limitName;
        }

        public String getLimitName() {
            return limitName;
        }

        public static Resource fromCode(Object code) {
            if (!(code instanceof String text)) return null;
            for (Resource resource : values()) {
                if (resource.name().equals(text)) return resource;
            }
            return null;
        }
    }
}
