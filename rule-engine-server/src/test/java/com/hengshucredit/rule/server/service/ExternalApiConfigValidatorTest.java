package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.junit.Test;
import static org.junit.Assert.assertThrows;

public class ExternalApiConfigValidatorTest {
    @Test
    public void missingAsyncSettingsFailBeforeAnySubmission() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setRequestMode("ASYNC");
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
        config.setAsyncResultMode("POLL");
        config.setAsyncPollConfig("{\"taskIdPath\":\"body.id\",\"statusPath\":\"body.status\",\"successValue\":\"DONE\"}");
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
        config.setAsyncPollConfig("{\"taskIdPath\":\"body.id\",\"statusPath\":\"body.status\",\"successValue\":\"DONE\",\"resultEndpointUrl\":\"/poll\",\"maxAttempts\":0}");
        config.setAsyncTimeoutMs(0);
        ExternalApiConfigValidator.validate(config);
    }

    @Test
    public void asyncWaitBudgetIsIndependentFromSingleHttpTimeout() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setRequestMode("ASYNC");
        config.setRequestMethod("POST");
        config.setTimeoutMs(3000);
        config.setAsyncTimeoutMs(60_000);
        config.setAsyncResultMode("POLL");
        config.setAsyncPollConfig("{\"taskIdPath\":\"body.id\",\"statusPath\":\"body.status\",\"successValue\":\"DONE\",\"resultEndpointUrl\":\"/poll\",\"intervalMs\":3000,\"maxAttempts\":20}");

        ExternalApiConfigValidator.validate(config);

        config.setAsyncTimeoutMs(999);
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
        config.setAsyncTimeoutMs(86_400_001);
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
    }

    @Test
    public void traceIdPathCanReplaceTaskIdForGenericPolling() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setRequestMode("ASYNC");
        config.setAsyncTimeoutMs(60_000);
        config.setAsyncResultMode("POLL");
        config.setAsyncPollConfig("{\"traceIdPath\":\"body.trace_id\",\"statusPath\":\"body.status\",\"successValue\":\"READY\",\"resultEndpointUrl\":\"/result/${traceId}\",\"maxAttempts\":0}");
        ExternalApiConfigValidator.validate(config);
    }

    @Test
    public void emptyTokenConditionCannotAccidentallyMatchEveryResponse() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        for (String value : new String[]{"{}", "{\"type\":\"group\",\"children\":[]}", "{\"operator\":\"==\",\"value\":1}"}) {
            config.setTokenFailureCondition(value);
            assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
        }
        config.setTokenFailureCondition("{\"path\":\"body.code\",\"operator\":\"==\",\"value\":\"TOKEN_EXPIRED\"}");
        ExternalApiConfigValidator.validate(config);
    }

    @Test
    public void invalidSuccessOrRetryConditionFailsBeforeExecution() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setSuccessCondition("{invalid-json");
        assertThrows(IllegalArgumentException.class,
                () -> ExternalApiConfigValidator.validate(config));

        config.setSuccessCondition(null);
        config.setRetryCondition("{\"type\":\"group\",\"children\":[]}");
        assertThrows(IllegalArgumentException.class,
                () -> ExternalApiConfigValidator.validate(config));

        config.setRetryCondition("{\"path\":\"body.code\",\"operator\":\"==\",\"value\":\"RETRY\"}");
        ExternalApiConfigValidator.validate(config);
    }

    @Test
    public void retryStrategyRequiresConfiguredRetryAttempt() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setExceptionStrategy("RETRY");
        config.setRetryCount(0);
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));

        config.setRetryCount(1);
        ExternalApiConfigValidator.validate(config);
    }

    @Test
    public void workflowStepExceptionConditionTreeIsValidatedBeforeExecution() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setRequestMode("ASYNC");
        config.setExecutionConfig("""
            {"version":2,"requestFields":[],"steps":[
              {"id":"provider","type":"HTTP","endpointUrl":"https://example.test/provider","requestFields":[],
               "exceptionConditionTree":{"type":"group","operator":"AND","children":[
                 {"operator":"missing"}]}}
            ]}
            """);
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));

        config.setExecutionConfig("""
            {"version":2,"requestFields":[],"steps":[
              {"id":"provider","type":"HTTP","endpointUrl":"https://example.test/provider","requestFields":[],
               "exceptionCondition":"{invalid-json"}
            ]}
            """);
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));

        config.setExecutionConfig("""
            {"version":2,"requestFields":[],"steps":[
              {"id":"provider","type":"HTTP","endpointUrl":"https://example.test/provider","requestFields":[],
               "exceptionConditionTree":{"type":"group","operator":"AND","children":[
                 {"path":"response.body.code","operator":"missing"}]}}
            ]}
            """);
        ExternalApiConfigValidator.validate(config);
    }

    @Test
    public void unsupportedAuthModeFailsBeforeExecution() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setAuthMode("UNKNOWN");
        assertThrows(IllegalArgumentException.class,
                () -> ExternalApiConfigValidator.validate(config));

        config.setAuthMode("inherit");
        ExternalApiConfigValidator.validate(config);
    }

    @Test
    public void payloadCaptureRejectsInvalidPathsAndAcceptsDecryptConfig() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setPayloadCaptureConfig("{\"response\":{\"excludePaths\":[\"$.items[*].base64\"],"
                + "\"decrypt\":{\"enabled\":true,\"mode\":\"BASE64\",\"path\":\"$.payload\"}}}");
        ExternalApiConfigValidator.validate(config);

        config.setPayloadCaptureConfig("{\"response\":{\"excludePaths\":[\"$.items[bad]\"]}}");
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));

        config.setPayloadCaptureConfig("{\"response\":{\"decrypt\":{\"enabled\":true,\"mode\":\"TRIPLE_DES_BASE64\",\"path\":\"$\"}}}");
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
    }
}
