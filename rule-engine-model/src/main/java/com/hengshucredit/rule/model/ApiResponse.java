package com.hengshucredit.rule.model;

/** 平台 JSON 响应；固定描述与本次请求的详细原因分开。 */
public class ApiResponse<T> {
    private PlatformCode status = PlatformCode.SUCCESS;
    private String detail;
    private String traceId;
    private T data;
    private transient ExternalCodeMapping externalMapping = ExternalCodeMapping.DEFAULT;

    public String getCode() { return externalMapping.code(status); }
    public String getMessage() { return externalMapping.message(status); }
    public boolean isSuccess() { return status == PlatformCode.SUCCESS; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public PlatformCode status() { return status; }
    public ApiResponse<T> mapExternal(ExternalCodeMapping mapping) {
        externalMapping = java.util.Objects.requireNonNull(mapping);
        if (data instanceof com.hengshucredit.rule.model.dto.RuleResult result) result.mapExternal(mapping);
        if (data instanceof com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult result) result.mapExternal(mapping);
        if (data instanceof com.hengshucredit.rule.model.dto.RuleExecutionStatus execution && execution.getResult() != null) execution.getResult().mapExternal(mapping);
        return this;
    }
    public void setStatus(PlatformCode status) { this.status = java.util.Objects.requireNonNull(status); }

    protected void initialize(PlatformCode status, String detail, T data) {
        setStatus(status);
        this.detail = detail;
        this.data = data;
    }

    protected void initializeData(T data) {
        this.data = data;
        if (data instanceof PlatformOutcome outcome) {
            setStatus(outcome.platformStatus());
            this.detail = outcome.platformDetail();
            this.traceId = outcome.platformTraceId();
        }
    }

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.initializeData(data);
        return response;
    }

    public static <T> ApiResponse<T> ok() { return ok(null); }

    public static <T> ApiResponse<T> fail(Throwable error) {
        return fail(PlatformErrors.classify(error), PlatformErrors.detail(error));
    }

    public static <T> ApiResponse<T> fail(PlatformCode code, String detail) {
        if (code == PlatformCode.SUCCESS) throw new IllegalArgumentException("失败响应不能使用成功码");
        ApiResponse<T> response = new ApiResponse<>();
        response.initialize(code, detail, null);
        return response;
    }

    public static <T> ApiResponse<T> fail(PlatformCode code, String detail, T data) {
        ApiResponse<T> response = fail(code, detail);
        response.setData(data);
        return response;
    }
}
