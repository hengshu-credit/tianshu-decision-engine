package com.hengshucredit.rule.server.openapi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 已发布开放接口的请求映射与统一响应模板。 */
public class OpenApiContract {

    private boolean enabled;
    private boolean recordTrace;
    private boolean returnTrace;
    private List<RequestMapping> requestMappings = new ArrayList<>();
    private Object envelopeTemplate;
    private String dataPath;
    private Object successDataTemplate;
    private Object errorDataTemplate;
    private Map<String, String> responseHeaders = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRecordTrace() {
        return recordTrace;
    }

    public void setRecordTrace(boolean recordTrace) {
        this.recordTrace = recordTrace;
    }

    public boolean isReturnTrace() {
        return returnTrace;
    }

    public void setReturnTrace(boolean returnTrace) {
        this.returnTrace = returnTrace;
    }

    public List<RequestMapping> getRequestMappings() {
        return requestMappings;
    }

    public void setRequestMappings(List<RequestMapping> requestMappings) {
        this.requestMappings = requestMappings == null ? new ArrayList<RequestMapping>() : requestMappings;
    }

    public Object getEnvelopeTemplate() {
        return envelopeTemplate;
    }

    public void setEnvelopeTemplate(Object envelopeTemplate) {
        this.envelopeTemplate = envelopeTemplate;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public Object getSuccessDataTemplate() {
        return successDataTemplate;
    }

    public void setSuccessDataTemplate(Object successDataTemplate) {
        this.successDataTemplate = successDataTemplate;
    }

    public Object getErrorDataTemplate() {
        return errorDataTemplate;
    }

    public void setErrorDataTemplate(Object errorDataTemplate) {
        this.errorDataTemplate = errorDataTemplate;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders == null ? new LinkedHashMap<String, String>() : responseHeaders;
    }

    public static class RequestMapping {
        private Long targetVarId;
        private String targetRefType;
        private String sourceType;
        private String sourcePath;
        private boolean required;
        private String defaultValue;
        private String targetType;

        public Long getTargetVarId() {
            return targetVarId;
        }

        public void setTargetVarId(Long targetVarId) {
            this.targetVarId = targetVarId;
        }

        public String getTargetRefType() {
            return targetRefType;
        }

        public void setTargetRefType(String targetRefType) {
            this.targetRefType = targetRefType;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getSourcePath() {
            return sourcePath;
        }

        public void setSourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getTargetType() {
            return targetType;
        }

        public void setTargetType(String targetType) {
            this.targetType = targetType;
        }
    }
}
