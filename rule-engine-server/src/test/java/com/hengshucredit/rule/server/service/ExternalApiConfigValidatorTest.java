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
}
