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
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(config));
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
    public void unsupportedAuthModeFailsBeforeExecution() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setAuthMode("UNKNOWN");
        assertThrows(IllegalArgumentException.class,
                () -> ExternalApiConfigValidator.validate(config));

        config.setAuthMode("inherit");
        ExternalApiConfigValidator.validate(config);
    }
}
