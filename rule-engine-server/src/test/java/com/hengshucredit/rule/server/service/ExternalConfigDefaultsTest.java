package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ExternalConfigDefaultsTest {

    @Test
    public void datasourceBlankAuthConfigBecomesNullForJsonColumn() throws Exception {
        RuleExternalDatasource datasource = new RuleExternalDatasource();
        datasource.setScope("GLOBAL");
        datasource.setAuthType("NONE");
        datasource.setAuthConfig("");

        invokeFillDefaults(new RuleExternalDatasourceService(), datasource);

        assertNull(datasource.getAuthConfig());
        assertEquals(Long.valueOf(0L), datasource.getProjectId());
    }

    @Test
    public void apiBlankJsonConfigsBecomeNullButMappingsArePreserved() throws Exception {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setHeaderConfig("");
        config.setQueryConfig("  ");
        config.setRequestMapping("{\"customerId\":\"$.customerId\"}");
        config.setResponseMapping("");
        config.setAuthApiConfig("");
        config.setRetryCondition("  ");

        invokeFillDefaults(new RuleExternalApiConfigService(), config);

        assertNull(config.getHeaderConfig());
        assertNull(config.getQueryConfig());
        assertEquals("{\"customerId\":\"$.customerId\"}", config.getRequestMapping());
        assertNull(config.getResponseMapping());
        assertNull(config.getAuthApiConfig());
        assertNull(config.getRetryCondition());
        assertNull(config.getContentType());
        assertEquals(Integer.valueOf(0), config.getResponseCacheSeconds());
    }

    @Test
    public void apiAsyncDefaultsPreserveSelectedAsyncConfigOnly() throws Exception {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setRequestMode("ASYNC");
        config.setAsyncResultMode("POLL");
        String pollConfig = "{\"resultEndpointUrl\":\"/result/${taskId}\",\"taskIdPath\":\"body.taskId\",\"statusPath\":\"body.status\",\"successValue\":\"DONE\"}";
        config.setAsyncPollConfig(pollConfig);
        config.setAsyncCallbackConfig("");
        config.setAsyncCallbackUrl("  ");
        config.setAsyncResultPath("body.data");

        invokeFillDefaults(new RuleExternalApiConfigService(), config);

        assertEquals("ASYNC", config.getRequestMode());
        assertEquals("POLL", config.getAsyncResultMode());
        assertEquals(pollConfig, config.getAsyncPollConfig());
        assertNull(config.getAsyncCallbackConfig());
        assertNull(config.getAsyncCallbackUrl());
        assertEquals("body.data", config.getAsyncResultPath());
    }

    @Test
    public void apiRequestAndAsyncModesAreNormalizedAndValidated() throws Exception {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setRequestMode(" async ");
        config.setAsyncResultMode(" poll ");
        config.setRequestMethod(" get ");
        config.setAsyncPollConfig("{\"resultEndpointUrl\":\"/result/${taskId}\",\"taskIdPath\":\"body.taskId\",\"statusPath\":\"body.status\",\"successValue\":\"DONE\"}");

        invokeFillDefaults(new RuleExternalApiConfigService(), config);

        assertEquals("ASYNC", config.getRequestMode());
        assertEquals("POLL", config.getAsyncResultMode());
        assertEquals("GET", config.getRequestMethod());
    }

    @Test
    public void apiResilienceSettingsRejectUnboundedRetriesAndInvalidStatusCodes() throws Exception {
        RuleExternalApiConfig excessiveRetries = new RuleExternalApiConfig();
        excessiveRetries.setRetryCount(11);
        assertInvalidApiConfig(excessiveRetries, "重试次数");

        RuleExternalApiConfig invalidStatuses = new RuleExternalApiConfig();
        invalidStatuses.setRetryStatusCodes("502,not-a-status");
        assertInvalidApiConfig(invalidStatuses, "重试状态码");
    }

    @Test
    public void apiExceptionStrategyIsNormalizedAndInvalidValuesRejected() throws Exception {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setExceptionStrategy(" use_cache ");
        invokeFillDefaults(new RuleExternalApiConfigService(), config);
        assertEquals("USE_CACHE", config.getExceptionStrategy());

        RuleExternalApiConfig invalid = new RuleExternalApiConfig();
        invalid.setExceptionStrategy("unknown");
        assertInvalidApiConfig(invalid, "异常处理策略");
    }

    @Test
    public void apiReferenceValidationRejectsCrossProjectObjects() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setDatasourceId(8L);
        RuleExternalDatasource datasource = new RuleExternalDatasource();
        datasource.setId(8L);
        datasource.setProjectId(7L);
        datasource.setScope("PROJECT");
        datasource.setStatus(1);
        RuleDataObject object = new RuleDataObject();
        object.setId(9L);
        object.setProjectId(99L);
        object.setScope("PROJECT");
        object.setStatus(1);

        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> ExternalApiConfigValidator.validateReferences(config, datasource, object, null));
    }

    private void assertInvalidApiConfig(RuleExternalApiConfig config, String expectedMessage) throws Exception {
        try {
            invokeFillDefaults(new RuleExternalApiConfigService(), config);
            org.junit.Assert.fail("Expected invalid external API configuration");
        } catch (InvocationTargetException exception) {
            org.junit.Assert.assertTrue(exception.getCause().getMessage(),
                    exception.getCause().getMessage().contains(expectedMessage));
        }
    }

    private void invokeFillDefaults(Object service, Object target) throws Exception {
        Method method = service.getClass().getDeclaredMethod("fillDefaults", target.getClass());
        method.setAccessible(true);
        method.invoke(service, target);
    }
}
