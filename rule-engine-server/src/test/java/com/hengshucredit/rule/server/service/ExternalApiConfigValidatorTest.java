package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.junit.Test;
import static org.junit.Assert.assertThrows;

public class ExternalApiConfigValidatorTest {
    @Test
    public void wholeResponseAllowsEmptyDestinationLikeRuntimeAndSamplePreview() {
        RuleExternalApiConfig config = ExternalApiTestConfig.api();
        config.setExecutionConfig("""
            {"version":2,"responseBranches":[{"id":"result","mode":"VALUE","outputPath":"",
              "value":{"kind":"PATH","value":"response.body"},"sample":{"score":88}}]}
            """);
        ExternalApiConfigValidator.validateEditableConfig(config);
        var branch = com.alibaba.fastjson.JSON.parseObject(config.getExecutionConfig()).getJSONArray("responseBranches").getJSONObject(0);
        org.junit.Assert.assertEquals(java.util.Map.of("score", 88), ExternalApiResponseSchema.sampleValue(branch));
        config.setRequestMode("ASYNC");
        config.setExecutionConfig("{\"version\":2,\"steps\":[{\"id\":\"call\",\"type\":\"HTTP\",\"endpointUrl\":\"/sample\",\"responseBranches\":[" + branch.toJSONString() + "]}]}");
        ExternalApiConfigValidator.validateEditableConfig(config);
    }
    @Test
    public void missingAsyncSettingsFailBeforeAnySubmission() {
        RuleExternalApiConfig config = ExternalApiTestConfig.api();
        config.setRequestMode("ASYNC");
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
        config.setExecutionConfig("{\"version\":2,\"requestFields\":[],\"steps\":[{\"id\":\"poll\",\"type\":\"HTTP\",\"poll\":{}}]}");
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
        config.setExecutionConfig(ExternalApiTestConfig.polling("/submit", "/poll", "body.status", "DONE", 0));
        config.setAsyncTimeoutMs(0);
        ExternalApiConfigValidator.validate(config);
    }

    @Test
    public void asyncWaitBudgetIsIndependentFromSingleHttpTimeout() {
        RuleExternalApiConfig config = ExternalApiTestConfig.api();
        config.setRequestMode("ASYNC");
        config.setRequestMethod("POST");
        config.setTimeoutMs(3000);
        config.setAsyncTimeoutMs(60_000);
        config.setExecutionConfig(ExternalApiTestConfig.polling("/submit", "/poll", "body.status", "DONE", 20));

        ExternalApiConfigValidator.validate(config);

        config.setAsyncTimeoutMs(999);
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
        config.setAsyncTimeoutMs(86_400_001);
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
    }

    @Test
    public void pollingCanReferenceAnyDeclaredSubmissionFieldByStep() {
        RuleExternalApiConfig config = ExternalApiTestConfig.api();
        config.setRequestMode("ASYNC");
        config.setAsyncTimeoutMs(60_000);
        config.setExecutionConfig(ExternalApiTestConfig.polling("/submit", "/result/${steps.submit.response.body.trace_id}", "body.status", "READY", 0));
        ExternalApiConfigValidator.validate(config);
    }

    @Test
    public void emptyTokenConditionCannotAccidentallyMatchEveryResponse() {
        RuleExternalApiConfig config = ExternalApiTestConfig.api();
        for (String value : new String[]{"{}", "{\"type\":\"group\",\"children\":[]}", "{\"operator\":\"==\",\"value\":1}"}) {
            config.setTokenFailureCondition(value);
            assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
        }
        config.setTokenFailureCondition("{\"path\":\"body.code\",\"operator\":\"==\",\"value\":\"TOKEN_EXPIRED\"}");
        ExternalApiConfigValidator.validate(config);
    }

    @Test
    public void invalidSuccessOrRetryConditionFailsBeforeExecution() {
        RuleExternalApiConfig config = ExternalApiTestConfig.api();
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
        RuleExternalApiConfig config = ExternalApiTestConfig.api();
        config.setExceptionStrategy("RETRY");
        config.setRetryCount(0);
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));

        config.setRetryCount(1);
        ExternalApiConfigValidator.validate(config);
    }

    @Test
    public void workflowStepExceptionConditionTreeIsValidatedBeforeExecution() {
        RuleExternalApiConfig config = ExternalApiTestConfig.api();
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
    public void responseBranchesValidateOutputShapeBeforeExecution() {
        RuleExternalApiConfig config = ExternalApiTestConfig.api();
        config.setExecutionConfig("""
            {"version":2,"responseBranches":[{"id":"result","mode":"FIELDS","outputFields":[
              {"id":"score","path":"score","value":{"kind":"PATH","value":"response.body.score"}},
              {"id":"duplicate","path":"score","value":{"kind":"LITERAL","value":0}}
            ]}]}
            """);
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));

        config.setExecutionConfig("""
            {"version":2,"responseBranches":[{"id":"result","mode":"VALUE"}]}
            """);
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));

        config.setExecutionConfig("""
            {"version":2,"steps":[{"id":"step","type":"HTTP","endpointUrl":"https://example.test/step",
              "responseBranches":[{"id":"result","mode":"FIELDS","outputFields":[
                {"id":"score","path":"score","value":{"kind":"PATH","value":"response.body.score"}}
              ]}]}]}
            """);
        config.setRequestMode("ASYNC");
        ExternalApiConfigValidator.validate(config);

        config.setExecutionConfig("""
            {"version":2,"steps":[{"id":"step","type":"HTTP","endpointUrl":"https://example.test/step",
              "responseBranches":[{"id":"same"},{"id":"same"}]}]}
            """);
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
    }

    @Test
    public void requestHeadersAreDeduplicatedCaseInsensitively() {
        RuleExternalApiConfig config = ExternalApiTestConfig.api();
        config.setExecutionConfig("""
            {"version":2,"requestFields":[
              {"id":"a","location":"HEADER","path":"Authorization","value":{"kind":"LITERAL","value":"a"}},
              {"id":"b","location":"HEADER","path":"authorization","value":{"kind":"LITERAL","value":"b"}}
            ]}
            """);
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
    }

    @Test
    public void legacyBillingConditionIsValidatedBeforeExecution() {
        RuleExternalApiConfig config = ExternalApiTestConfig.api();
        config.setBillingCondition("{\"mode\":\"HIT\",\"condition\":{\"operator\":\"==\",\"value\":1}}");
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
        config.setBillingCondition("{\"mode\":\"QUERY\"}");
        ExternalApiConfigValidator.validate(config);
    }

    @Test
    public void unsupportedAuthModeFailsBeforeExecution() {
        RuleExternalApiConfig config = ExternalApiTestConfig.api();
        config.setAuthMode("UNKNOWN");
        assertThrows(IllegalArgumentException.class,
                () -> ExternalApiConfigValidator.validate(config));

        config.setAuthMode("inherit");
        ExternalApiConfigValidator.validate(config);
    }

    @Test
    public void payloadCaptureRejectsInvalidPathsAndAcceptsDecryptConfig() {
        RuleExternalApiConfig config = ExternalApiTestConfig.api();
        config.setPayloadCaptureConfig("{\"response\":{\"excludePaths\":[\"$.items[*].base64\"],"
                + "\"decrypt\":{\"enabled\":true,\"mode\":\"BASE64\",\"path\":\"$.payload\"}}}");
        ExternalApiConfigValidator.validate(config);

        config.setPayloadCaptureConfig("{\"response\":{\"excludePaths\":[\"$.items[bad]\"]}}");
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));

        config.setPayloadCaptureConfig("{\"response\":{\"decrypt\":{\"enabled\":true,\"mode\":\"TRIPLE_DES_BASE64\",\"path\":\"$\"}}}");
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
    }
}
